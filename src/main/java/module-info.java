module filesponge {
	requires org.apache.logging.log4j;
	requires dbengine;
	requires it.unimi.dsi.fastutil;
	requires org.jetbrains.annotations;
	requires reactor.core;
	requires org.reactivestreams;
	requires it.cavallium.datagen;
	exports org.warp.filesponge;
}