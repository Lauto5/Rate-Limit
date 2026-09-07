package io.github.lauto5.rateLimit.application;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import org.junit.jupiter.api.Test;

import io.github.lauto5.rateLimit.application.ports.out.StateCodec;
import io.github.lauto5.rateLimit.domain.algorithmState.AlgorithmState;

class VersionedStateCodecUnitTest {

	private static final FakeStateCodec DELEGATE = new FakeStateCodec();

	private final VersionedStateCodec<FakeState> codec = new VersionedStateCodec<>(DELEGATE);

	@Test
	void encodeShouldPrefixMagicAndVersionHeader() {
		byte[] encoded = codec.encode(new FakeState("abc"));
		assertEquals('R', encoded[0]);
		assertEquals('L', encoded[1]);
		assertEquals(0x01, encoded[2]);
		assertArrayEquals("abc".getBytes(StandardCharsets.UTF_8),
				Arrays.copyOfRange(encoded, 3, encoded.length));
	}

	@Test
	void decodeShouldRoundTripWhenHeaderIsValid() {
		byte[] encoded = codec.encode(new FakeState("abc"));
		assertEquals("DECODED:abc", codec.decode(encoded).payload);
	}

	@Test
	void decodeShouldRejectTruncatedData() {
		assertThrows(CorruptedStateException.class, () -> codec.decode(new byte[] { 'R', 'L' }));
		assertThrows(CorruptedStateException.class, () -> codec.decode(new byte[0]));
		assertThrows(CorruptedStateException.class, () -> codec.decode(null));
	}

	@Test
	void decodeShouldRejectInvalidMagic() {
		byte[] bad = "XX01abc".getBytes(StandardCharsets.UTF_8);
		assertThrows(CorruptedStateException.class, () -> codec.decode(bad));
	}

	@Test
	void decodeShouldRejectUnsupportedVersion() {
		byte[] bad = new byte[] { 'R', 'L', 0x02, 'a' };
		assertThrows(CorruptedStateException.class, () -> codec.decode(bad));
	}

	private static final class FakeState implements AlgorithmState {

		private final String payload;

		private FakeState(String payload) {
			this.payload = payload;
		}
	}

	private static final class FakeStateCodec implements StateCodec<FakeState> {

		@Override
		public byte[] encode(FakeState state) {
			return state.payload.getBytes(StandardCharsets.UTF_8);
		}

		@Override
		public FakeState decode(byte[] data) {
			return new FakeState("DECODED:" + new String(data, StandardCharsets.UTF_8));
		}
	}
}