package io.zumba.core.rdt

import java.util.concurrent.atomic.AtomicInteger

/**
 * For reliable data transfer, it is important that the remote actors and the main node are working in sequence.
 * Iff the sequence is maintained, the consistency of data is guaranteed.
 */
object RDT {
	private[this] val sequence = new AtomicInteger
	
	/**
	 * Gets the next sequence number to be used while sending message to client
	 */
	def nextSequence() = sequence.incrementAndGet()
	
	/**
	 * Returns the current sequence number running
	 */
	def currentSequence = sequence.get
}