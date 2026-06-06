package com.guanyu.haigui.pojo.vo;

import lombok.Data;

@Data
public class PublishResponseVO {
    /** ACCEPTED=已提交异步发布, PUBLISHING=已有任务进行中 */
    private String status;
    private String message;

    public static PublishResponseVO accepted() {
        PublishResponseVO vo = new PublishResponseVO();
        vo.setStatus("ACCEPTED");
        vo.setMessage("已提交发布任务，向量化完成后将自动上架");
        return vo;
    }

    public static PublishResponseVO publishing() {
        PublishResponseVO vo = new PublishResponseVO();
        vo.setStatus("PUBLISHING");
        vo.setMessage("该海龟汤正在发布中，请稍候");
        return vo;
    }
}
