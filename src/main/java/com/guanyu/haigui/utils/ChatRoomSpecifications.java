package com.guanyu.haigui.utils;

import com.guanyu.haigui.pojo.dto.LobbyListDTO;
import com.guanyu.haigui.pojo.model.ChatGame;
import jakarta.persistence.criteria.*;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class ChatRoomSpecifications {

    /**
     * 根据LobbyListDTO生成动态查询条件（含关联对象预加载）
     */
    public static Specification<ChatGame> filterByLobbyListDTO(LobbyListDTO dto) {
        return (Root<ChatGame> root, CriteriaQuery<?> query, CriteriaBuilder cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            // 1. 预加载关联对象（避免懒加载异常）
            root.fetch("creator", JoinType.LEFT); // 加载创建者（UserInfo）
            root.fetch("members", JoinType.LEFT); // 加载成员列表（ChatRoomMember）

            // 2. 聊天室名称模糊查询
            if (dto.getRoomName() != null && !dto.getRoomName().isEmpty()) {
                predicates.add(cb.like(root.get("roomName"), "%" + dto.getRoomName() + "%"));
            }

            // 3. 状态精确匹配
            if (dto.getStatus() != null) {
                predicates.add(cb.equal(root.get("status"), dto.getStatus()));
            }

            // 4. 所需成员数 ≥ 输入值
            if (dto.getRequiredMembers() != null) {
                predicates.add(cb.ge(root.get("requiredMembers"), dto.getRequiredMembers()));
            }

            // 5. 当前成员数 = 输入值
            if (dto.getCurrentMembers() != null) {
                predicates.add(cb.equal(root.get("currentMembers"), dto.getCurrentMembers()));
            }

            // 6. 创建时间匹配（若需范围查询，可扩展为between）
            if (dto.getCreateTime() != null) {
                predicates.add(cb.equal(root.get("createTime"), dto.getCreateTime()));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }
}