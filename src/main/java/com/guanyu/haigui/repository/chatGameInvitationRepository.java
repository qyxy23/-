package com.guanyu.haigui.repository;

import com.guanyu.haigui.Enum.InvitationStatus;
import com.guanyu.haigui.pojo.model.ChatGameInvitation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface chatGameInvitationRepository extends JpaRepository<ChatGameInvitation, Long> {


    boolean existsByChatGameRoomIdAndInviteeUserIdAndStatus(String roomId, Long inviteeId, InvitationStatus invitationStatus) ;

}
