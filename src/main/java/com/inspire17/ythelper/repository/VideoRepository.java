package com.inspire17.ythelper.repository;

import com.inspire17.ythelper.entity.ChannelEntity;
import com.inspire17.ythelper.entity.VideoEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface VideoRepository extends JpaRepository<VideoEntity, String> {
    List<VideoEntity> findByChannel(ChannelEntity channel);

}
