module filesponge {
	requires org.apache.logging.log4j;
	requires dbengine;
	requires it.unimi.dsi.fastutil;
	requires org.jetbrains.annotations;
	requires reactor.core;
	requires org.reactivestreams;
	exports org.warp.filesponge;
}