package io.github.lauto5.rateLimit.api;

import io.github.lauto5.rateLimit.domain.algorithm.FixedWindowAlgorithm;
import io.github.lauto5.rateLimit.domain.algorithm.FixedWindowAlgorithmImpl;

public class Algorithm {

	public static FixedWindowAlgorithm fixedWindow() {
		return new FixedWindowAlgorithmImpl();
	}
	
}
