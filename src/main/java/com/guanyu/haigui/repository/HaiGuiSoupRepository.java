package com.guanyu.haigui.repository;

import com.guanyu.haigui.pojo.model.HaiGuiSoup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface HaiGuiSoupRepository extends JpaRepository<HaiGuiSoup, String> {

}
