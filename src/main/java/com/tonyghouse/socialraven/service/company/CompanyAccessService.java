package com.tonyghouse.socialraven.service.company;

import com.tonyghouse.socialraven.constant.UserStatus;
import com.tonyghouse.socialraven.constant.UserType;
import com.tonyghouse.socialraven.constant.WorkspaceRole;
import com.tonyghouse.socialraven.entity.CompanyEntity;
import com.tonyghouse.socialraven.entity.CompanyUserEntity;
import com.tonyghouse.socialraven.entity.UserProfileEntity;
import com.tonyghouse.socialraven.entity.WorkspaceEntity;
import com.tonyghouse.socialraven.entity.WorkspaceMemberEntity;
import com.tonyghouse.socialraven.exception.SocialRavenException;
import com.tonyghouse.socialraven.repo.CompanyRepo;
import com.tonyghouse.socialraven.repo.CompanyUserRepo;
import com.tonyghouse.socialraven.repo.UserProfileRepo;
import com.tonyghouse.socialraven.repo.WorkspaceMemberRepo;
import com.tonyghouse.socialraven.repo.WorkspaceRepo;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@Slf4j
public class CompanyAccessService {

    @Autowired
    private CompanyRepo companyRepo;

    @Autowired
    private CompanyUserRepo companyUserRepo;

    @Autowired
    private WorkspaceRepo workspaceRepo;

    @Autowired
    private WorkspaceMemberRepo workspaceMemberRepo;

    @Autowired
    private UserProfileRepo userProfileRepo;

    @Transactional
    public CompanyEntity createCompany(String ownerUserId,
                                       String companyName,
                                       String logoS3Key,
                                       OffsetDateTime now,
                                       String preferredCompanyId) {
        CompanyEntity company = new CompanyEntity();
        company.setId(preferredCompanyId != null ? preferredCompanyId : UUID.randomUUID().toString());
        company.setName(companyName);
        company.setOwnerUserId(ownerUserId);
        company.setLogoS3Key(logoS3Key);
        company.setCreatedAt(now);
        company.setUpdatedAt(now);
        companyRepo.save(company);

        ensureCompanyUser(company.getId(), ownerUserId, WorkspaceRole.OWNER, now);
        refreshWorkspaceCreationPermission(ownerUserId);
        return company;
    }

    public CompanyEntity requireCompany(String companyId) {
        return companyRepo.findById(companyId)
                .orElseThrow(() -> new SocialRavenException("Company not found", HttpStatus.NOT_FOUND));
    }

    public List<CompanyUserEntity> getUserCompanies(String userId) {
        return companyUserRepo.findAllByUserId(userId);
    }

    public Set<String> getManageableCompanyIds(String userId) {
        Set<String> companyIds = new LinkedHashSet<>();
        for (CompanyUserEntity companyUser : companyUserRepo.findAllByUserId(userId)) {
            if (companyUser.getRole().isAtLeast(WorkspaceRole.ADMIN)) {
                companyIds.add(companyUser.getCompanyId());
            }
        }
        return companyIds;
    }

    public Optional<String> findPrimaryCompanyId(String userId) {
        return companyUserRepo.findAllByUserId(userId).stream()
                .sorted(Comparator
                        .comparingInt((CompanyUserEntity companyUser) -> companyUser.getRole().rank())
                        .reversed()
                        .thenComparing(CompanyUserEntity::getJoinedAt)
                        .thenComparing(CompanyUserEntity::getCompanyId))
                .map(CompanyUserEntity::getCompanyId)
                .findFirst();
    }

    public String resolveManageableCompanyId(String userId, String requestedCompanyId) {
        Set<String> manageableCompanyIds = getManageableCompanyIds(userId);
        if (requestedCompanyId != null && !requestedCompanyId.isBlank()) {
            if (!manageableCompanyIds.contains(requestedCompanyId)) {
                throw new SocialRavenException("ADMIN or OWNER access to the company is required", HttpStatus.FORBIDDEN);
            }
            return requestedCompanyId;
        }

        if (manageableCompanyIds.isEmpty()) {
            throw new SocialRavenException("You do not have permission to create workspaces", HttpStatus.FORBIDDEN);
        }
        if (manageableCompanyIds.size() > 1) {
            throw new SocialRavenException("companyId is required when you can manage multiple companies", HttpStatus.BAD_REQUEST);
        }
        return manageableCompanyIds.iterator().next();
    }

    @Transactional
    public void ensureCompanyUser(String companyId, String userId, WorkspaceRole role, OffsetDateTime joinedAt) {
        CompanyEntity company = requireCompany(companyId);
        WorkspaceRole effectiveRole = company.getOwnerUserId().equals(userId) ? WorkspaceRole.OWNER : role;

        companyUserRepo.findByCompanyIdAndUserId(companyId, userId).ifPresentOrElse(existing -> {
            if (effectiveRole.rank() > existing.getRole().rank()) {
                existing.setRole(effectiveRole);
                companyUserRepo.save(existing);
            }
        }, () -> {
            CompanyUserEntity companyUser = new CompanyUserEntity();
            companyUser.setCompanyId(companyId);
            companyUser.setUserId(userId);
            companyUser.setRole(effectiveRole);
            companyUser.setJoinedAt(joinedAt);
            companyUserRepo.save(companyUser);
        });

        refreshWorkspaceCreationPermission(userId);
    }

    @Transactional
    public void syncCompanyUserRole(String companyId, String userId) {
        CompanyEntity company = requireCompany(companyId);
        OffsetDateTime now = OffsetDateTime.now();

        if (company.getOwnerUserId().equals(userId)) {
            ensureCompanyUser(companyId, userId, WorkspaceRole.OWNER, now);
            return;
        }

        Optional<WorkspaceRole> highestRole = workspaceMemberRepo.findAllByUserId(userId).stream()
                .filter(membership -> belongsToCompany(membership.getWorkspaceId(), companyId))
                .map(WorkspaceMemberEntity::getRole)
                .max(Comparator.comparingInt(WorkspaceRole::rank));

        if (highestRole.isPresent()) {
            ensureCompanyUser(companyId, userId, highestRole.get(), now);
        } else {
            companyUserRepo.findByCompanyIdAndUserId(companyId, userId).ifPresent(companyUserRepo::delete);
            refreshWorkspaceCreationPermission(userId);
        }
    }

    @Transactional
    public void refreshWorkspaceCreationPermission(String userId) {
        boolean canCreate = companyUserRepo.findAllByUserId(userId).stream()
                .anyMatch(companyUser -> companyUser.getRole().isAtLeast(WorkspaceRole.ADMIN));

        userProfileRepo.findById(userId).ifPresentOrElse(profile -> {
            if (profile.isCanCreateWorkspaces() != canCreate) {
                profile.setCanCreateWorkspaces(canCreate);
                profile.setUpdatedAt(OffsetDateTime.now());
                userProfileRepo.save(profile);
            }
        }, () -> {
            UserProfileEntity profile = new UserProfileEntity();
            profile.setUserId(userId);
            profile.setUserType(UserType.INFLUENCER);
            profile.setStatus(UserStatus.ACTIVE);
            profile.setCanCreateWorkspaces(canCreate);
            profile.setCreatedAt(OffsetDateTime.now());
            profile.setUpdatedAt(OffsetDateTime.now());
            userProfileRepo.save(profile);
        });
    }

    private boolean belongsToCompany(String workspaceId, String companyId) {
        return workspaceRepo.findById(workspaceId)
                .map(WorkspaceEntity::getCompanyId)
                .filter(companyId::equals)
                .isPresent();
    }
}
