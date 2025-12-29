package com.guanyu.haigui.pojo.Info;

import com.guanyu.haigui.Enum.ClueType;
import com.guanyu.haigui.converter.ListStringConverter;
import jakarta.persistence.Convert;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
@Data
public class ClueFragmentInfo {
    private String fragmentContent;

    private ClueType fragmentType;

    private Integer inferenceLevel = 1;

    private Integer difficulty = 2;

    private Integer importance = 5;

    @Convert(converter = ListStringConverter.class)
    private List<String> triggerKeywords;

    private BigDecimal similarityThreshold = BigDecimal.valueOf(0.7);

    private Boolean isCoreClue = false;

    private Integer fragmentOrder = 0;

    private String generationSource = "AI";
}
