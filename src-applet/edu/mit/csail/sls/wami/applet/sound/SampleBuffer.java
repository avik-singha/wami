/* -*- java -*-
 *
 * Copyright (c) 2008
 * Spoken Language Systems Group
 * MIT Computer Science and Artificial Intelligence Laboratory
 * Massachusetts Institute of Technology
 *
 * Permission is hereby granted, free of charge, to any person
 * obtaining a copy of this software and associated documentation
 * files (the "Software"), to deal in the Software without
 * restriction, including without limitation the rights to use, copy,
 * modify, merge, publish, distribute, sublicense, and/or sell copies
 * of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS
 * BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN
 * ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 * CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package edu.mit.csail.sls.wami.applet.sound;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.channels.ReadableByteChannel;
import java.nio.channels.WritableByteChannel;
import java.util.TreeMap;

/** Provides unlimited buffering with multiple readers.  Readers are
 * thread-safe with respect to the writer and other readers, but the
 * writer and the individual readers are not themselves thread-safe.
 * In other words, it is safe to have one thread for writing and one
 * thread for each reader, but it is not safe to have multiple threads
 * writing or multiple threads reading from a particular reader
 * without additional concurrency control.  It is safe for one thread
 * to write and/or make use of multiple readers.
 *
 * Data is stored in a list of buffers.  The buffer granularity
 * specifies the size of each buffer.
 **/
public class SampleBuffer implements WritableByteChannel, InputStreamWriter {
	final int capacity;		// Size of one buffer in bytes
	final int nchannels;	// Number of channels
	final int sampleBytes;	// Bytes in one sample for one channel
	final int frameSize;	// Bytes for one sample for all channels
	volatile boolean isOpen = true;
	volatile Segment tail;	// Writes go here
	volatile Segment head;	// Reads start here
	volatile Segment free = null; // Free list
	volatile long position = 0;	// Last readable byte position
	TreeMap<Long,Segment> segments = new TreeMap<Long,Segment>();
	
	/** Allocate a SampleBuffer with nchannels, with each channel
	 * consisting of sampleBytes bytes per sample.  Buffers will be
	 * allocated in bufferGranularity-sized chunks.
	 *
	 * @param nchannels The number of channels
	 *
	 * @param frameSize The numbers of bytes in one frame
	 *
	 * @param bufferGranularity The number of bytes for one chunk of
	 * buffer.  This will be rounded down so that the bytes for a
	 * channel will not cross a buffer boundary.
	 *
	 **/
	public SampleBuffer(int nchannels, int frameSize, int bufferGranularity){
		this.nchannels = nchannels;
		this.frameSize = frameSize;
		this.sampleBytes = frameSize/nchannels;
		// Make a multiple of frameSize 
		this.capacity = (bufferGranularity/frameSize)*frameSize;
		tail = getSegment(0);
		tail.lock();
		head = tail;
	}
	
	/** Allocate a SampleBuffer with nchannels, with each channel
	 * consisting of sampleBytes bytes per sample.  Buffers will be
	 * allocated in bufferGranularity-sized chunks.
	 *
	 * @param nchannels The number of channels
	 *
	 * @param frameSize The numbers of bytes in one frame
	 **/
	public SampleBuffer(int nchannels, int frameSize){
		this(nchannels, frameSize, 1024);
	}
	
	/** Allocate a single-channel SampleBuffer of bytes with granularity 1024.
	 **/
	public SampleBuffer(){
		this(1,1);
	}
	
	/* Holds one segment of buffer
	 *
	 */
	protected class Segment {
		ByteBuffer buffer = ByteBuffer.allocate(capacity);
		int count = 0;		// Users of segment
		Segment next = null;
		long position = 0;	// Byte position for buffer[0]
		
		/* Must be called with synchronization
		 *
		 * Removes the segment from the active segments and puts it on
		 * the free list initialized for its next use.
		 */
		final void release(){
			segments.remove(new Long(position));
			buffer.clear();
			count = 0;
			next = free;
			free = this;
		}
		
		final void lock(){
			count++;
		}
		
		final void unlock(){
			if (count == 0){
				throw new IllegalArgumentException
				("Attempt to release a lock on position "+position
						+" which is not locked");
			}
			count--;
			
			while(head.count == 0){
				Segment seg = head;
				head = seg.next;
				seg.release();
			}
		}
	}
	
	/* Get a segment for a segment-aligned position, either from the
	 * segments tree, the free list, or by allocating a new one.
	 * offset is a byte offset
	 */
	synchronized Segment getSegment(long offset){
		if (head !=null && offset < head.position){
			throw new IllegalArgumentException
			("Position "+offset+
					" is no longer available.  First available byte is "
					+head.position);
		}
		
		offset -= (offset % capacity);
		Long key = new Long(offset);
		Segment segment = (Segment)segments.get(key);
		
		if (segment == null){
			
			if (free == null){
				segment = new Segment();
			} else {
				segment = free;
				free = segment.next;
			}
			
			segments.put(key, segment);
		}
		segment.position = offset;
		return segment;
	}
	
	final long byteFromSample(long sampleOffset){
		return sampleOffset == Long.MAX_VALUE
		? Long.MAX_VALUE : sampleOffset*frameSize;
	}
	
	final long sampleFromByte(long byteOffset){
		return byteOffset == Long.MAX_VALUE
		? Long.MAX_VALUE : byteOffset/frameSize;
	}
	
	/** Adds a lock to bytes starting at the indicated offset, so that
	 * reader stream can be positioned anywhere between this offset
	 * and the last written byte.
	 *
	 * @param sampleOffset The sample position to start the lock.
	 *
	 **/
	public synchronized void lockPosition(long sampleOffset){
		if (sampleOffset == Long.MAX_VALUE)
			return;
		
		getSegment(byteFromSample(sampleOffset)).lock();
	}
	
	/** Release a lock on a previously locked position, freeing any
	 * segments that are no longer needed.
	 *
	 * @param sampleOffset The position to be unlocked.  The position must
	 * have already been locked.
	 *
	 **/
	public synchronized void unlockPosition(long sampleOffset){
		if (sampleOffset == Long.MAX_VALUE)
			return;
		
		getSegment(byteFromSample(sampleOffset)).unlock();
	}
	
	/** Safely move a locked position to a new position.
	 *
	 * @param oldSampleOffset The lock position to release
	 *
	 * @param newSampleOffset The lock position to take
	 *
	 **/
	public synchronized void moveLockPosition(long oldSampleOffset,
			long newSampleOffset){
		if (oldSampleOffset != newSampleOffset){
			lockPosition(newSampleOffset);
			unlockPosition(oldSampleOffset);
		}
	}
	
	/* Prepares the next tail segment, either by taking one from the
	 * free list or allocating a new one.
	 */
	protected synchronized void newTail(){
		Segment last = tail;
		tail = getSegment(tail.position+capacity);
		tail.lock();
		last.next = tail;
		last.unlock();
	}
	
	/** True if the buffer is open.
	 *
	 **/
	public synchronized boolean isOpen(){
		return isOpen;
	}
	
	/** Close the buffer, preventing additional data from being
	 * written.  Readers can continue.
	 *
	 **/
	public synchronized void close(){
		isOpen = false;
		notifyAll();
	}
	
	/** Appends data to the samples.  This is protected against
	 * multiple reader threads, but not against multiple writers.
	 *
	 * @param src The data to append
	 **/
	public int write(ByteBuffer src){
		int result = src.remaining();
		while(true){
			ByteBuffer dest = tail.buffer;
			int nSrc = src.remaining();
			if (nSrc == 0){
				synchronized(this){
					position += result;
					notifyAll();
					return result;
				}
			}
			
			int nTail = dest.remaining();
			if (nTail == 0){
				newTail();
				dest = tail.buffer;
				nTail = dest.remaining();
			}
			
			int n = Math.min(nTail, nSrc);
			int limit = src.limit();
			src.limit(src.position()+n);
			dest.put(src);
			src.limit(limit);
		}
	}
	
	/** A SampleBuffer could be filled by a channel, a stream, or a
	 * TargetDataLine, none of which share a common interface.  A
	 * SampleBuffer writer can implement this interface to call the
	 * appropriate read method and pass the implementation to write.
	 **/
	public interface DataReader {
		/** Read data
		 *
		 * @param dest Bytes to read into
		 *
		 * @param off offset
		 *
		 * @param len length
		 *
		 * @return The number of bytes read, or -1 for end of file.
		 *
		 **/
		int read(byte[] dest, int off, int len) throws IOException;
		
		/** Cleanup any allocated resources.
		 *
		 **/
		void close();
	}
	
	/** Write into the buffer via a callback that will read from a
	 * data source.
	 *
	 * If the reader indicates end of file, the sample buffer is not
	 * closed.  This allows several streams to be concatenated.
	 *
	 * @param reader The reader.read method will be called with a
	 * byte[], position, and length to fill the next segment or
	 * portion of a segment of the buffer.
	 *
	 * @return The value returned by reader.read
	 *
	 **/
	public int write(DataReader reader) throws IOException {
		if (!tail.buffer.hasRemaining()){
			newTail();
		}
		ByteBuffer bb = tail.buffer;
		int pos = bb.position();
		int len = bb.remaining();
		int nread = reader.read(bb.array(), pos, len);
		if (nread <= 0){
			reader.close();
			return -1;
		}
		bb.position(pos+nread);
		synchronized(this){
			position += nread;
			notifyAll();
		}
		return nread;
	}
	
	public void write(final InputStream is) throws IOException {
		DataReader reader = new DataReader(){
			public int read(byte[] dest, int off, int len) 
			throws IOException {
				return is.read(dest, off, len);
			}
			
			public void close(){
				try {
					is.close();
				} catch(IOException e){
				}
			}
		};
		while(write(reader) > 0){
		}
	}
	
	/** The position in the SampleBuffer of the next byte to be
	 * written.
	 *
	 **/
	public synchronized long position(){
		return position;
	}
	
	/** Returns a SampleBuffer reader
	 *
	 **/
	public Reader reader(Limit limit){
		return new Reader(limit);
	}
	
	public Reader reader(Limit limit, int channel){
		if (channel == 0 && nchannels == 1)
			return new Reader(limit);
		else
			return new ChannelReader(limit, channel);
	}
	
	public synchronized Reader reader(){
		return reader(new Limit(sampleFromByte(head.position), 
				Long.MAX_VALUE,
				Long.MAX_VALUE));
	}
	
	public synchronized Reader reader(int channel){
		return reader(new Limit(sampleFromByte(head.position), 
				Long.MAX_VALUE,
				Long.MAX_VALUE),
				channel);
	}
	
	public class Limit {
		volatile long limit = Long.MAX_VALUE; // Byte position
		final long bofPosition;	// Byte position
		volatile long eofPosition = Long.MAX_VALUE; // Byte position
		
		Limit(long bofSamplePosition, 
				long limitSample,
				long eofSamplePosition){
			bofPosition = byteFromSample(bofSamplePosition);
			limit = byteFromSample(limitSample);
			eofPosition = byteFromSample(eofSamplePosition);
		}
		
		/** Moves the reader limit forward.  Readers using this limit
		 * will block if they try to read beyond this offset.
		 *
		 * @param sampleOffset The new sample offset 
		 *
		 **/
		public void limit(long sampleOffset){
			long offset = byteFromSample(sampleOffset);
			
			if (offset < limit){
				throw new IllegalArgumentException
				("Cannot move limit backwards from "
						+sampleFromByte(limit)+" to "+sampleOffset);
			}
			
			if (offset == limit)
				return;
			
			synchronized(SampleBuffer.this){
				limit = offset;
				SampleBuffer.this.notifyAll();
			}
		}
		
		/** The current limit in samples
		 *
		 **/
		public long limit(){
			return sampleFromByte(byteLimit());
		}
		
		long byteLimit(){
			synchronized(SampleBuffer.this){
				return limit;
			}
		}
		
		/** Sets the eof position.  When readers reach the eof position,
		 * they will see an end of file.
		 *
		 * @param sampleOffset The sample offset for the eof mark.  This
		 * cannot be before the limit.
		 *
		 **/
		public void eofPosition(long sampleOffset){
			long offset = byteFromSample(sampleOffset);
			
			synchronized(SampleBuffer.this){
				if (offset < limit){
					throw new IllegalArgumentException
					("Cannot set eof position before limit");
				}
				eofPosition = offset;
				SampleBuffer.this.notifyAll();
			}
		}
		
		long eofBytePosition(){
			synchronized(SampleBuffer.this){
				return isOpen ? 
						eofPosition : Math.min(eofPosition, position);
			}
		}
		
		/** The current eof in samples position.
		 * 
		 **/
		public long eofPosition(){
			return sampleFromByte(eofBytePosition());
		}
		
	}
	
	public Limit createLimit(long bofSampleOffset){
		return new Limit(bofSampleOffset, bofSampleOffset, Long.MAX_VALUE);
	}
	
	/** A reader for a sample buffer.  Reads can safely happen
	 * concurrently with writes to the buffer, but individual readers
	 * do not provide safe access from multiple threads.
	 * This reader reads all channels
	 **/
	public class Reader implements ReadableByteChannel {
		volatile Segment rhead = null; // Reader's buffer position
		volatile ByteBuffer buffer; // Reader's buffer
		volatile boolean eof;
		volatile boolean readerIsOpen;
		Limit limit;
		volatile long readerPosition; // Byte offset in sample buffer
		volatile boolean blocking = true;
		
		Reader(Limit limit){
			readerIsOpen = true;
			synchronized(SampleBuffer.this){
				this.limit = limit;
				eof = false;
				bufferPosition(limit.bofPosition);
			}
		}
		
		/** Enable or disable blocking during reads (the default is
		 * for blocking reads).
		 *
		 * @param blocking Whether or not reads block
		 *
		 **/
		public void setBlocking(boolean blocking){
			synchronized(SampleBuffer.this){
				this.blocking = blocking;
				SampleBuffer.this.notifyAll();
			}
		}
		
		public boolean blocking(){
			synchronized(SampleBuffer.this){
				return blocking;
			}
		}
		
		/** Close the reader, releasing resources.
		 *
		 **/
		public void close(){
			synchronized(SampleBuffer.this){
				if (readerIsOpen){
					readerIsOpen = false;
					if (rhead != null)
						rhead.unlock();
					SampleBuffer.this.notifyAll();
				}
			}
		}
		
		/** Returns true if the reader is open.
		 *
		 **/
		public boolean isOpen(){
			return readerIsOpen;
		}
		
		/** Returns the current byte position of this reader in the
		 * underlying buffer
		 */
		public long bufferPosition(){
			synchronized(SampleBuffer.this){
				return readerPosition;
			}
		}
		
		/** Sets the byte position of this reader in the underlying
		 * buffer
		 *
		 * @param newPosition The new byte offset for the reader.
		 * This must within the limits specified by the associated
		 * limit for the reader, as well as in a locked portion of the
		 * buffer.
		 **/
		public void bufferPosition(long newPosition){
			synchronized(SampleBuffer.this){
				if (newPosition < limit.bofPosition){
					throw new IllegalArgumentException
					("Position out of range for reader");
				}
				Segment oldseg = rhead;
				readerPosition = newPosition;
				rhead = getSegment(readerPosition);
				if (rhead != oldseg){
					rhead.lock();
					if (oldseg != null){
						oldseg.unlock();
					}
					buffer = rhead.buffer.duplicate();
				}
				
				// Expand the buffer and reposition.  Read will block
				// until there is something to read and then set the
				// limit to the proper value
				int offset = (int)(readerPosition-rhead.position);
				buffer.limit(offset);
				buffer.position(offset);
				eof = readerPosition >= limit.eofBytePosition();
				
				SampleBuffer.this.notifyAll();
			}
		}
		
		/** Will block until there might be something to read.
		 *
		 **/
		public void waitReady(){
			synchronized(SampleBuffer.this){
				if (eof || (readerPosition >= limit.eofPosition())){
					return;
				} else if (readerPosition >= 
					Math.min(limit.byteLimit(), position)){
					// Need to wait for the data to appear
					try {
						SampleBuffer.this.wait();
					} catch(InterruptedException e){
					}		
				}
			}
		}
		
		
		/** Read into dest, blocking if bytes are not available.
		 *
		 * @param dest The byte buffer to write into
		 *
		 * @return The number of bytes read, or -1 if the end of
		 * the sample buffer has been reached.
		 **/
		public int read(ByteBuffer dest){
			if (eof)
				return -1;
			
			synchronized(SampleBuffer.this){
				int result = 0;
				int destRemaining = dest.remaining();
				getBytes:
					while(destRemaining > 0 && !eof){
						if (readerPosition >= limit.eofBytePosition()){
							// Nothing more to read
							eof = true;
							break;
						} else if (readerPosition >= 
							Math.min(limit.byteLimit(), position)){
							// Need to wait for the data to appear
							try {
								if (!blocking)
									break getBytes;
								SampleBuffer.this.wait();
							} catch(InterruptedException e){
							}			
						} else {
							// Update the buffer limit if more data has been 
							// written, but clip it to the limit
							buffer.limit
							((int)
									(Math.min(rhead.buffer.position(),
											limit.byteLimit()-rhead.position)));
							
							int bufferRemaining = buffer.remaining();
							if (bufferRemaining > 0){
								// Copy what's in the buffer
								int size = Math.min(destRemaining,bufferRemaining);
								if (bufferRemaining > destRemaining){
									int saveLimit = buffer.limit();
									buffer.limit(buffer.position()+destRemaining);
									dest.put(buffer);
									buffer.limit(saveLimit);
								} else {
									dest.put(buffer);
								}
								result+=size;
								destRemaining-=size;
								readerPosition+=size;
							} else {
								// Move to the next segment
								Segment oldseg = rhead;
								rhead = rhead.next;
								rhead.lock();
								oldseg.unlock();
								buffer = rhead.buffer.duplicate();
								buffer.flip();
							}
						}
					}
				return result;
			}
		}
		
		/** The position in the SampleBuffer of the next byte to be read,
		 * relative the the beginning of the limit.
		 *
		 **/
		public long position(){
			return bufferPosition()-limit.bofPosition;
		}
		
		/** Set the position to a new location, relative to the limit.
		 * This is not interlocked with read.
		 *
		 * @param newPosition
		 *
		 **/
		public void position(long newPosition){
			bufferPosition(newPosition+limit.bofPosition);
		}
		
		/** Returns true if an EOF mark has been passed in the input
		 *
		 **/
		public boolean eof(){
			return eof;
		}
		
		/** The number of bytes between the current position and the last
		 * readable byte.
		 **/
		public int remaining(){
			synchronized(SampleBuffer.this){
				return (int)
				(Math.min(position,limit.byteLimit())
						-(rhead.position+buffer.position()));
			}
		}
	}
	
	/** Reads samples that are interleaved with others, as would be
	 * seen on an n-channel stream.  All positioning is relative to
	 * the bytes in the interleaved stream, not the multi-channel
	 * stream
	 **/
	public class ChannelReader extends Reader {
		final int channel;
		
		ChannelReader(Limit limit, int channel){
			super(limit);
			this.channel = channel;
		}
		
		/** Returns the number of interleaved bytes between the
		 * current reader position and the beginning of the limit.
		 * Bytes from other channels are not included.
		 *
		 **/
		@Override
		public long position(){
			return super.position()/nchannels;
		}
		
		/** Sets the reader position to the nth byte of the channel's
		 * data, relative to the beginning of the limit.
		 *
		 * @param newPosition The byte position.
		 *
		 **/
		@Override
		public void position(long newPosition){
			super.position(nchannels*newPosition);
		}
		
		/** The number of bytes between the current position and the last
		 * readable byte.
		 **/
		@Override
		public int remaining(){
			return (super.remaining()/frameSize)*sampleBytes;
		}
		
		/** Read into dest, blocking if bytes are not available.
		 *
		 * @param dest The byte buffer to write into
		 *
		 * @return The number of bytes read, or -1 if the end of
		 * the sample buffer has been reached.
		 **/
		@Override
		public int read(ByteBuffer dest){
			if (eof)
				return -1;
			
			synchronized(SampleBuffer.this){
				int result = 0;
				int destRemaining = dest.remaining();
				getBytes:
					while(destRemaining > 0 && !eof){
						if (readerPosition >= limit.eofBytePosition()){
							// Nothing more to read
							eof = true;
							break;
						} else if (readerPosition >= 
							Math.min(limit.byteLimit(), position)){
							// Need to wait for the data to appear
							try {
								if (!blocking)
									break getBytes;
								SampleBuffer.this.wait();
							} catch(InterruptedException e){
							}			
						} else {
							// Update the buffer limit if more data has been 
							// written, but clip it to the limit
							buffer.limit
							((int)
									(Math.min(rhead.buffer.position(),
											limit.byteLimit()-rhead.position)));
							
							// Quantize by samples
							int framesRemaining = buffer.remaining()/frameSize;
							int bufferRemaining = sampleBytes*framesRemaining;
							int destFramesRemaining = destRemaining/sampleBytes;
							
							// Changes start here
							if (bufferRemaining > 0){
								// Copy what's in the buffer
								int nframes = Math.min(destFramesRemaining,
										framesRemaining);
								int size = nframes*sampleBytes;
								int frameBytes = nframes*frameSize;
								int base = buffer.position()+channel*sampleBytes;
								result+=size;
								destRemaining-=size;
								readerPosition+=frameBytes;
								while(size > 0){
									for(int i=0; i<sampleBytes; i++){
										dest.put(buffer.get(base++));
									}
									size-=sampleBytes;
									base+=(frameSize-sampleBytes);
								}
								buffer.position(buffer.position()+frameBytes);
							} else {
								// Move to the next segment
								Segment oldseg = rhead;
								rhead = rhead.next;
								rhead.lock();
								oldseg.unlock();
								buffer = rhead.buffer.duplicate();
								buffer.flip();
							}
						}
					}
				return result;
			}
		}
	}
}
