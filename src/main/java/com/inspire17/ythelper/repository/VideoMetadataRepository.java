package com.inspire17.ythelper.repository;

import com.inspire17.ythelper.document.VideoMetadata;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface VideoMetadataRepository extends MongoRepository<VideoMetadata, String> {
}
