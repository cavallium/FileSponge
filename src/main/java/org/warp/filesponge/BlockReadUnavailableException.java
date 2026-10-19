package org.warp.filesponge;

/** A provider cannot perform a direct block read; another provider may be tried. */
public final class BlockReadUnavailableException extends UnsupportedOperationException {
	public BlockReadUnavailableException(String message) {
		super(message);
	}
}
