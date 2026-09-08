package io.github.lauto5.rateLimit.application;

import java.util.Arrays;

import io.github.lauto5.rateLimit.application.ports.out.StateCodec;
import io.github.lauto5.rateLimit.domain.algorithmState.AlgorithmState;

/**
 * Decorator that prefixes state serialization with a format marker for versioning.
 *
 * <p>El payload serializado tiene esta forma:
 *
 * <pre>
 * [0x52][0x4C][0x01][... payload del codec delegado ...]
 *   ^      ^      ^
 *   'R'    'L'   version (1)
 * </pre>
 *
 * Al decodificar se valida el marcador; ante datos truncados, corruptos o de una version no
 * soportada se lanza {@link CorruptedStateException} en lugar de intentar interpretar datos
 * ambiguos.
 *
 * <p><b>Politica de versionado:</b> actualmente existe una unica version (0x01) y no hay
 * migraciones. La frontera de versionado queda definida asi:
 *
 * <ul>
 *   <li>una version desconocida produce {@link CorruptedStateException} (nunca se interpretan
 *       bytes ambiguos);</li>
 *   <li>el store Redis trata ese estado como inexistente (politica <em>fail-open</em>) y lo
 *       reescribe;</li>
 *   <li>un rolling deployment con formatos distintos solo es posible introduciendo una nueva
 *       version y conservando lectores para las anteriores.</li>
 * </ul>
 *
 * @param <S> the concrete algorithm state type
 */
public final class VersionedStateCodec<S extends AlgorithmState> implements StateCodec<S> {

	private static final byte[] MAGIC = { 'R', 'L' };
	private static final byte CURRENT_VERSION = 0x01;
	private static final int HEADER_LENGTH = MAGIC.length + 1;

	private final StateCodec<S> delegate;

	/**
	 * Creates a codec that wraps the given delegate with versioning.
	 *
	 * @param delegate the codec that knows how to serialize a concrete algorithm state
	 */
	public VersionedStateCodec(StateCodec<S> delegate) {
		this.delegate = delegate;
	}

	@Override
	public byte[] encode(S state) {
		byte[] payload = delegate.encode(state);
		byte[] framed = new byte[HEADER_LENGTH + payload.length];
		framed[0] = MAGIC[0];
		framed[1] = MAGIC[1];
		framed[2] = CURRENT_VERSION;
		System.arraycopy(payload, 0, framed, HEADER_LENGTH, payload.length);
		return framed;
	}

	@Override
	public S decode(byte[] data) {
		if (data == null || data.length < HEADER_LENGTH) {
			throw new CorruptedStateException("Estado truncado o inexistente");
		}
		if (data[0] != MAGIC[0] || data[1] != MAGIC[1]) {
			throw new CorruptedStateException(
					"Marcador de formato invalido: " + describeBytes(data));
		}
		if (data[2] != CURRENT_VERSION) {
			throw new CorruptedStateException(
					"Version de formato no soportada: " + (data[2] & 0xFF));
		}

		byte[] payload = Arrays.copyOfRange(data, HEADER_LENGTH, data.length);

		try {
			return delegate.decode(payload);
		} catch (RuntimeException e) {
			/*
			 * El header es valido pero el codec concreto no pudo interpretar el payload
			 * (por ejemplo, un NumberFormatException del codec de FixedWindow). Se normaliza
			 * a CorruptedStateException para que el store aplique su politica fail-open:
			 * tratar el estado como inexistente y reescribirlo, en lugar de fallar la request.
			 */
			throw new CorruptedStateException(
					"Payload de estado ilegible: " + e.getMessage(), e);
		}
	}

	private static String describeBytes(byte[] data) {
		int len = Math.min(data.length, 4);
		StringBuilder hex = new StringBuilder(len * 2);
		for (int i = 0; i < len; i++) {
			hex.append(String.format("%02X", data[i] & 0xFF));
		}
		return hex.toString();
	}
}