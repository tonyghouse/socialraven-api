package com.ghouse.socialraven.service.workspace;

import com.ghouse.socialraven.constant.WorkspaceRole;
import com.ghouse.socialraven.dto.workspace.CreateWorkspaceRequest;
import com.ghouse.socialraven.dto.workspace.UpdateWorkspaceRequest;
import com.ghouse.socialraven.dto.workspace.WorkspaceResponse;
import com.ghouse.socialraven.entity.PlanConfigEntity;
import com.ghouse.socialraven.entity.UserPlanEntity;
import com.ghouse.socialraven.entity.WorkspaceEntity;
import com.ghouse.socialraven.entity.WorkspaceMemberEntity;
import com.ghouse.socialraven.exception.SocialRavenException;
import com.ghouse.socialraven.repo.PlanConfigRepo;
import com.ghouse.socialraven.repo.UserPlanRepo;
import com.ghouse.socialraven.repo.WorkspaceMemberRepo;
import com.ghouse.socialraven.repo.WorkspaceRepo;
import com.ghouse.socialraven.util.WorkspaceContext;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Slf4j
public class WorkspaceService {

    @Autowired
    private WorkspaceRepo workspaceRepo;

    @Autowired
    private WorkspaceMemberRepo workspaceMemberRepo;

    @Autowired
    private UserPlanRepo userPlanRepo;

    @Autowired
    private PlanConfigRepo planConfigRepo;

    /**
     * Returns all workspaces the caller belongs to, with their role in each.
     */
    public List<WorkspaceResponse> getMyWorkspaces(String userId) {
        List<WorkspaceMemberEntity> memberships = workspaceMemberRepo.findAllByUserId(userId);
        return memberships.stream()
                .map(m -> {
                    WorkspaceEntity ws = workspaceRepo.findById(m.getWorkspaceId())
                            .orElse(null);
                    if (ws == null) return null;
                    return toResponse(ws, m.getRole());
                })
                .filter(r -> r != null)
                .collect(Collectors.toList());
    }

    /**
     * Creates a new workspace for the caller.
     * Plan-gated: checks max_workspaces against how many workspaces the user already owns.
     */
    @Transactional
    public WorkspaceResponse createWorkspace(String userId, CreateWorkspaceRequest req) {
        if (req.getName() == null || req.getName().isBlank()) {
            throw new SocialRavenException("Workspace name is required", HttpStatus.BAD_REQUEST);
        }

        // Plan-gate: count current owned workspaces vs allowed limit
        UserPlanEntity planEntity = userPlanRepo.findByUserId(userId).orElse(null);
        if (planEntity != null) {
            PlanConfigEntity config = planConfigRepo.findById(planEntity.getPlanType()).orElse(null);
            if (config != null && config.getMaxWorkspaces() > 0) {
                long ownedCount = workspaceRepo.findAllByOwnerUserId(userId).size();
                if (ownedCount >= config.getMaxWorkspaces()) {
                    throw new SocialRavenException(
                            "Workspace limit reached for your plan (" + config.getMaxWorkspaces() + ")",
                            HttpStatus.FORBIDDEN);
                }
            }
        }

        String workspaceId = UUID.randomUUID().toString();
        OffsetDateTime now = OffsetDateTime.now();

        WorkspaceEntity workspace = new WorkspaceEntity();
        workspace.setId(workspaceId);
        workspace.setName(req.getName().trim());
        workspace.setCompanyName(req.getCompanyName());
        workspace.setLogoS3Key(req.getLogoS3Key());
        workspace.setOwnerUserId(userId);
        workspace.setCreatedAt(now);
        workspace.setUpdatedAt(now);
        workspaceRepo.save(workspace);

        WorkspaceMemberEntity member = new WorkspaceMemberEntity();
        member.setWorkspaceId(workspaceId);
        member.setUserId(userId);
        member.setRole(WorkspaceRole.OWNER);
        member.setJoinedAt(now);
        workspaceMemberRepo.save(member);

        log.info("Workspace created: id={}, owner={}", workspaceId, userId);
        return toResponse(workspace, WorkspaceRole.OWNER);
    }

    /**
     * Returns workspace details. Caller must be a member (enforced by WorkspaceAccessFilter).
     */
    public WorkspaceResponse getWorkspace(String workspaceId, String userId) {
        WorkspaceEntity workspace = workspaceRepo.findById(workspaceId)
                .orElseThrow(() -> new SocialRavenException("Workspace not found", HttpStatus.NOT_FOUND));
        WorkspaceRole role = workspaceMemberRepo.findByWorkspaceIdAndUserId(workspaceId, userId)
                .map(WorkspaceMemberEntity::getRole)
                .orElseThrow(() -> new SocialRavenException("Access denied", HttpStatus.FORBIDDEN));
        return toResponse(workspace, role);
    }

    /**
     * Updates workspace details. Caller must be ADMIN or OWNER.
     */
    @Transactional
    public WorkspaceResponse updateWorkspace(String workspaceId, String userId, UpdateWorkspaceRequest req) {
        WorkspaceEntity workspace = workspaceRepo.findById(workspaceId)
                .orElseThrow(() -> new SocialRavenException("Workspace not found", HttpStatus.NOT_FOUND));

        WorkspaceRole role = workspaceMemberRepo.findByWorkspaceIdAndUserId(workspaceId, userId)
                .map(WorkspaceMemberEntity::getRole)
                .orElseThrow(() -> new SocialRavenException("Access denied", HttpStatus.FORBIDDEN));

        if (role != WorkspaceRole.OWNER && role != WorkspaceRole.ADMIN) {
            throw new SocialRavenException("ADMIN or OWNER role required", HttpStatus.FORBIDDEN);
        }

        if (req.getName() != null && !req.getName().isBlank()) {
            workspace.setName(req.getName().trim());
        }
        if (req.getCompanyName() != null) {
            workspace.setCompanyName(req.getCompanyName());
        }
        if (req.getLogoS3Key() != null) {
            workspace.setLogoS3Key(req.getLogoS3Key());
        }
        workspace.setUpdatedAt(OffsetDateTime.now());
        workspaceRepo.save(workspace);

        return toResponse(workspace, role);
    }

    /**
     * Deletes a workspace. Caller must be OWNER.
     */
    @Transactional
    public void deleteWorkspace(String workspaceId, String userId) {
        WorkspaceEntity workspace = workspaceRepo.findById(workspaceId)
                .orElseThrow(() -> new SocialRavenException("Workspace not found", HttpStatus.NOT_FOUND));

        if (!workspace.getOwnerUserId().equals(userId)) {
            throw new SocialRavenException("Only the workspace owner can delete it", HttpStatus.FORBIDDEN);
        }

        // Prevent deleting personal workspace
        if (workspaceId.startsWith("personal_")) {
            throw new SocialRavenException("Personal workspace cannot be deleted", HttpStatus.BAD_REQUEST);
        }

        workspaceRepo.delete(workspace);
        log.info("Workspace deleted: id={}, by userId={}", workspaceId, userId);
    }

    private WorkspaceResponse toResponse(WorkspaceEntity ws, WorkspaceRole role) {
        return new WorkspaceResponse(
                ws.getId(),
                ws.getName(),
                ws.getCompanyName(),
                ws.getOwnerUserId(),
                ws.getLogoS3Key(),
                role,
                ws.getCreatedAt(),
                ws.getUpdatedAt()
        );
    }
}
