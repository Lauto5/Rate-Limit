package io.github.lauto5.rateLimit.domain.algorithm;

import java.nio.charset.StandardCharsets;

/**
 * Shared wire format for the state codecs of algorithms that serialize exactly two scalar
 * fields as a pipe-delimited UTF-8 string (for example {@code count|windowStartMillis}).
 *
 * <p>Package-private: only the algorithm state codecs of this package use it. The codecs keep
 * their own typed parsing of the returned fields.
 */
final class PipeDelimitedCodec {

	private static final String FIELD_DELIMITER = "|";

	private static final String FIELD_DELIMITER_REGEX = "\\|";

	private PipeDelimitedCodec() {
	}

	/**
	 * @param first  the first field value
	 * @param second the second field value
	 * @return the two values joined by the pipe delimiter, encoded as UTF-8
	 */
	static byte[] encode(String first, String second) {
		return (first + FIELD_DELIMITER + second).getBytes(StandardCharsets.UTF_8);
	}

	/**
	 * @param data a pipe-delimited UTF-8 payload produced by {@link #encode(String, String)}
	 * @return the payload split on the pipe delimiter
	 */
	static String[] decode(byte[] data) {
		return new String(data, StandardCharsets.UTF_8).split(FIELD_DELIMITER_REGEX);
	}
}