package com.guanyu.haigui.pojo.vo;

import lombok.Data;

@Data
public class AddAuditUserVO {
    //状态
    private String status;
    //信息
    private String message;

    public static AddAuditUserVO error(String message) {
        AddAuditUserVO addAuditUserVO = new AddAuditUserVO();
        addAuditUserVO.setStatus("error");
        addAuditUserVO.setMessage(message);
        return addAuditUserVO;
    }

    public static AddAuditUserVO success() {
        AddAuditUserVO addAuditUserVO = new AddAuditUserVO();
        addAuditUserVO.setStatus("success");
        addAuditUserVO.setMessage("添加成功");
        return addAuditUserVO;
    }
}
