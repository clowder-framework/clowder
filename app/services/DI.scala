package services

import play.api.Play.current
import com.google.inject.Guice
import com.google.inject.AbstractModule

/**
 * Guide module configuration.
 *
 *
 */
object DI {
    lazy val injector = Guice.createInjector(new ConfigurationModule)
}

/**
 * Default production module.
 */
class ConfigurationModule extends AbstractModule {
  protected def configure() {
    bind(classOf[AppConfigurationService]).to(get("service.appConfiguration", "services.mongodb.MongoDBAppConfigurationService"))
    bind(classOf[UserService]).to(get("service.users", "services.mongodb.MongoDBUserService"))

    // ByteStorageService is used to store the actual bytes
    bind(classOf[ByteStorageService]).to(get("service.byteStorage", "services.mongodb.MongoDBByteStorage"))

    bind(classOf[DatasetService]).to(get("service.datasets", "services.mongodb.MongoDBDatasetService"))
    bind(classOf[FileService]).to(get("service.files", "services.mongodb.MongoDBFileService"))
    bind(classOf[SpaceService]).to(get("service.spaces", "services.mongodb.MongoDBSpaceService"))
    bind(classOf[MultimediaQueryService]).to(get("service.multimediaQuery", "services.mongodb.MongoDBMultimediaQueryService"))
    bind(classOf[CollectionService]).to(get("service.collections", "services.mongodb.MongoDBCollectionService"))
    bind(classOf[TagService]).to(get("service.tags", "services.mongodb.MongoDBTagService"))
    bind(classOf[ExtractorService]).to(get("service.extractors", "services.mongodb.MongoDBExtractorService"))
    bind(classOf[ExtractionRequestsService]).to(get("service.extractionRequests", "services.mongodb.MongoDBExtractionRequestsService"))
    bind(classOf[SectionService]).to(get("service.sections", "services.mongodb.MongoDBSectionService"))
    bind(classOf[CommentService]).to(get("service.comments", "services.mongodb.MongoDBCommentService"))
    bind(classOf[PreviewService]).to(get("service.previews", "services.mongodb.MongoDBPreviewService"))
    bind(classOf[ExtractionService]).to(get("service.extractions", "services.mongodb.MongoDBExtractionService"))
    bind(classOf[TempFileService]).to(get("service.tempFiles", "services.mongodb.MongoDBTempFileService"))
    bind(classOf[ThreeDService]).to(get("service.3D", "services.mongodb.MongoDBThreeDService"))
    bind(classOf[RdfSPARQLService]).to(get("service.RdfSPARQL", "services.fourstore.FourStoreRdfSPARQLService"))
    bind(classOf[ThumbnailService]).to(get("service.thumbnails", "services.mongodb.MongoDBThumbnailService"))
    bind(classOf[TileService]).to(get("service.tiles", "services.mongodb.MongoDBTileService"))
    bind(classOf[SectionIndexInfoService]).to(get("service.sectionIndexInfo", "services.mongodb.MongoDBSectionIndexInfoService"))
    bind(classOf[RelationService]).to(get("service.relations", "services.mongodb.MongoDBRelationService"))
    bind(classOf[ContextLDService]).to(get("service.ContextLDService", "services.mongodb.MongoDBContextLDService"))
    bind(classOf[EventService]).to(get("service.events", "services.mongodb.MongoDBEventService"))
    bind(classOf[SchedulerService]).to(get("service.jobs", "services.mongodb.MongoDBSchedulerService"))
    bind(classOf[CurationService]).to(get("service.curationObjs", "services.mongodb.MongoDBCurationService"))
    bind(classOf[MetadataService]).to(get("service.metadata", "services.mongodb.MongoDBMetadataService"))
    bind(classOf[FolderService]).to(get("service.folders", "services.mongodb.MongoDBFolderService"))
    bind(classOf[LogoService]).to(get("service.logos", "services.mongodb.MongoDBLogoService"))
    bind(classOf[VocabularyService]).to(get("service.vocabularies", "services.mongodb.MongoDBVocabularyService"))
    bind(classOf[VocabularyTermService]).to(get("service.vocabularyterms", "services.mongodb.MongoDBVocabularyTermService"))
    bind(classOf[StandardVocabularyService]).to(get("service.standardvocabulary", "services.mongodb.MongoDBStandardVocabularyService"))
    bind(classOf[SelectionService]).to(get("service.select", "services.mongodb.MongoDBSelectionService"))
    bind(classOf[FileLinkService]).to(get("service.FileLinkService", "services.mongodb.MongoDBFileLinkService"))
    bind(classOf[LogService]).to(get("service.LogService", "services.GraylogService"))
  }

  protected def get[T](key: String, missing: String) : Class[T] = {
    val name = current.configuration.getString(key).getOrElse(missing)
    Class.forName(name).asInstanceOf[Class[T]]
  }
}
