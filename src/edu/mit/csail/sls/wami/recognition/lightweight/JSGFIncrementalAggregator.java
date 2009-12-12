/* -*- Java -*-
 *
 * Copyright (c) 2009
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
package edu.mit.csail.sls.wami.recognition.lightweight;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.mit.csail.sls.wami.recognition.IRecognitionResult;

/**
 * Create an incremental agreggator, and keep giving it partials. It will
 * callback the requested method with whatever updates are appropriate. Some
 * potentially useful public static methods are provided as well.
 * 
 * @author alexgru
 * 
 */
public class JSGFIncrementalAggregator {
	public static String DEFAULT_SPLIT_TAG = "command";

	private JSGFIncrementalAggregatorListener listener;
	private String lastPartial;
	List<LinkedHashMap<String, String>> lastCommandSets;
	private int partialPendingIndex = 0;
	private String splitTagName;
	private boolean ignoreHangingTags;
	private boolean includeCommandMetaInfo;

	public JSGFIncrementalAggregator(JSGFIncrementalAggregatorListener listener) {
		this(listener, DEFAULT_SPLIT_TAG, true, false);
	}

	public JSGFIncrementalAggregator(
			JSGFIncrementalAggregatorListener listener, String splitTag) {
		this(listener, splitTag, true, false);
	}

	public JSGFIncrementalAggregator(
			JSGFIncrementalAggregatorListener listener, String splitTag,
			boolean ignoreHangingTags, boolean includeCommandMetaInfo) {
		this.splitTagName = splitTag;
		this.listener = listener;
		this.ignoreHangingTags = ignoreHangingTags;
		this.includeCommandMetaInfo = includeCommandMetaInfo;
		reset();
	}

	public void update(IRecognitionResult recResult) {
		if (recResult.getHyps().size() > 0) {
			update(recResult.getHyps().get(0), recResult.isIncremental());
		}
	}

	private boolean equalCommandSets(List<LinkedHashMap<String, String>> set1,
			List<LinkedHashMap<String, String>> set2) {

		if (set1 == null && set2 == null) {
			return true;
		} else if (set1 == null || set2 == null) {
			return false;
		} else if (set1.size() != set2.size()) {
			return false;
		} else {
			for (int i = 0; i < set1.size(); i++) {
				LinkedHashMap<String, String> hash1 = set1.get(i);
				LinkedHashMap<String, String> hash2 = set2.get(i);
				if (!hash1.equals(hash2)) {
					return false;
				}
			}
			return true;
		}
	}

	public void update(String hyp, boolean isPartial) {
		if (hyp == null || (isPartial && hyp.equals(lastPartial))) {
			return; // nothing to do
		} else {
			lastPartial = hyp;
		}

		List<LinkedHashMap<String, String>> commandSets = extractCommandSets(
				hyp, splitTagName, isPartial, ignoreHangingTags,
				includeCommandMetaInfo);

		if (isPartial && equalCommandSets(commandSets, lastCommandSets)) {
			return; // same as last time, nothing to do
		}
		lastCommandSets = commandSets;

		if (commandSets.size() == 1) {
			listener.processIncremental(commandSets.get(0), isPartial);
		} else {
			if (isPartial) {
				for (int i = partialPendingIndex; i < commandSets.size(); i++) {
					listener.processIncremental(commandSets.get(i),
							i == (commandSets.size() - 1));
				}
				partialPendingIndex = commandSets.size() - 1;
			} else {
				// flush out everything b/c we're done
				for (int i = partialPendingIndex; i < commandSets.size(); i++) {
					listener.processIncremental(commandSets.get(i), false);
				}
				partialPendingIndex = 0;
			}
		}

		if (!isPartial) {
			reset();
		}
	}

	/**
	 * Resets the aggregator
	 */
	public void reset() {
		partialPendingIndex = 0;
		lastPartial = null;
		lastCommandSets = null;
	}

	/**
	 * extract sets of key values, grouped into commands. To avoid some command
	 * ambiguity issues, if the hyp string ends with a tag, and that tag is a
	 * command we ignore it
	 * 
	 * @param includeCommandMetaInfo
	 * 
	 * @param tags
	 * @param endsWithTag
	 * @return
	 */
	public static List<LinkedHashMap<String, String>> extractCommandSets(
			String hyp, String splitTagName, boolean isPartial,
			boolean ignoreHangingTags, boolean includeCommandMetaInfo) {
		ArrayList<LinkedHashMap<String, String>> commandSets = new ArrayList<LinkedHashMap<String, String>>();
		ArrayList<Tag> tags = extractTags(hyp);
		boolean endsWithTag = hyp.trim().endsWith("]");

		LinkedHashMap<String, String> kvs = new LinkedHashMap<String, String>();
		for (int i = 0; i < tags.size(); i++) {
			Tag tag = tags.get(i);

			if (tag.getKey().equals(splitTagName)) {
				if (i == tags.size() - 1 && endsWithTag && ignoreHangingTags
						&& isPartial) {
					break; // ignore command tag if it's hanging at the end
				}

				if (kvs.size() > 0) {
					commandSets.add(kvs);
					kvs = new LinkedHashMap<String, String>();
				}

				if (includeCommandMetaInfo) {
					kvs.put("current_hypothesis", hyp);
					kvs.put("index_into_hypothesis", "" + tag.getIndex());
				}
			}

			kvs.put(tag.getKey(), tag.getValue());
		}

		commandSets.add(kvs);
		return commandSets;
	}

	private static class Tag {
		String key;
		String value;
		int index;

		public Tag(String tag, int index) {
			String[] kv = tag.split("=");
			key = kv[0].trim();
			value = kv[1].trim();
			this.index = index;
		}

		public String getKey() {
			return key;
		}

		public String getValue() {
			return value;
		}

		public int getIndex() {
			return index;
		}
	}

	public static ArrayList<Tag> extractTags(String hyp) {
		Matcher m = Pattern.compile("\\[(.*?)\\]").matcher(hyp);
		ArrayList<Tag> tags = new ArrayList<Tag>();
		while (m.find()) {
			tags.add(new Tag(m.group(1), m.start()));
		}

		return tags;
	}
}
