package io.github.lauto5.rateLimit.domain.algorithm;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

class PipeDelimitedCodecTest {

	@Test
	void encodeJoinsBothFieldsWithPipeDelimiterAsUtf8() {

		byte[] encoded = PipeDelimitedCodec.encode("3", "1750000000000");

		assertEquals("3|1750000000000", new String(encoded, StandardCharsets.UTF_8));
	}

	@Test
	void decodeSplitsThePayloadOnThePipeDelimiter() {

		byte[] payload = "1.5|42".getBytes(StandardCharsets.UTF_8);

		String[] fields = PipeDelimitedCodec.decode(payload);

		assertEquals(2, fields.length);
		assertEquals("1.5", fields[0]);
		assertEquals("42", fields[1]);
	}

	@Test
	void encodeFollowedByDecodePreservesTheOriginalFieldValues() {

		byte[] encoded = PipeDelimitedCodec.encode("3", "1750000000000");

		String[] fields = PipeDelimitedCodec.decode(encoded);

		assertArrayEquals(new String[] { "3", "1750000000000" }, fields);
	}
}