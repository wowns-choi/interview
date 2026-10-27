package com.example.interview.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppSettingMapper {
    String find(@Param("key") String key);

    int update(@Param("key") String key, @Param("value") String value);

    void insert(@Param("key") String key, @Param("value") String value);
}
