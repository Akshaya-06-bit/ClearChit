package com.clearchit.service;

import com.clearchit.model.ChitGroup;
import com.clearchit.model.Member;
import com.clearchit.model.UserAccount;
import com.clearchit.repository.ChitGroupMemberRepository;
import com.clearchit.repository.UserAccountRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

@Service
public class AuthorizationService {
    private final UserAccountRepository userAccountRepository;
    private final ChitGroupMemberRepository membershipRepository;
    public AuthorizationService(UserAccountRepository u, ChitGroupMemberRepository m){userAccountRepository=u;membershipRepository=m;}

    public UserAccount currentUser(){
        Authentication a=SecurityContextHolder.getContext().getAuthentication();
        if(a==null || !a.isAuthenticated()) throw new AccessDeniedException("Authentication required");
        return userAccountRepository.findByEmail(a.getName()).orElseThrow(() -> new AccessDeniedException("Authenticated user not found"));
    }
    public Member currentMember(){
        Member m=currentUser().getMember();
        if(m==null) throw new AccessDeniedException("Authenticated user is not linked to a member");
        return m;
    }
    public void requireMember(Long memberId){
        if(!currentMember().getId().equals(memberId)) throw new AccessDeniedException("You can only access your own member account");
    }
    public void requireGroupOwner(ChitGroup group){
        Member m=currentMember();
        if(group.getOwner()==null || !group.getOwner().getId().equals(m.getId())) throw new AccessDeniedException("You do not own this chit group");
    }
    public void requireGroupMember(Long groupId,Long memberId){
        requireMember(memberId);
        if(!membershipRepository.existsByChitGroupIdAndMemberId(groupId,memberId)) throw new AccessDeniedException("You are not a member of this chit group");
    }
}
