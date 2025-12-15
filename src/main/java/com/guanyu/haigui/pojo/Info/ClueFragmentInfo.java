package com.guanyu.haigui.pojo.Info;

import com.guanyu.haigui.converter.ListStringConverter;
import jakarta.persistence.Convert;
import lombok.Data;

import java.util.List;
@Data
public class ClueFragmentInfo {
    private String fragmentContent;

    private String fragmentType;

    private Integer inferenceLevel = 1;

    private Integer difficulty = 2;

    private Integer importance = 5;

    @Convert(converter = ListStringConverter.class)
    private List<String> triggerKeywords;

    private Double similarityThreshold = 0.7;

    private Boolean isCoreClue = false;

    private Integer fragmentOrder = 0;

    private String generationSource = "AI";

}
