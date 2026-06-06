package com.guanyu.haigui.pojo.vo;

import lombok.Data;

@Data
public class GenerateInfoResponseVO {
    /** ACCEPTED=已提交异步任务, GENERATING=已有任务进行中 */
    private String status;
    private String message;

    public static GenerateInfoResponseVO accepted() {
        GenerateInfoResponseVO vo = new GenerateInfoResponseVO();
        vo.setStatus("ACCEPTED");
        vo.setMessage("已提交 AI 生成任务，完成后将自动保存草稿");
        return vo;
    }

    public static GenerateInfoResponseVO generating() {
        GenerateInfoResponseVO vo = new GenerateInfoResponseVO();
        vo.setStatus("GENERATING");
        vo.setMessage("该海龟汤正在生成中，请稍候");
        return vo;
    }
}
