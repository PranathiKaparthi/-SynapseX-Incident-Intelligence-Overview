package com.synapsex.incident.repository;

import com.synapsex.incident.model.ServiceDependency;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface ServiceDependencyRepository extends JpaRepository<ServiceDependency, UUID> {
    List<ServiceDependency> findByWorkspaceId(UUID workspaceId);
    List<ServiceDependency> findByWorkspaceIdAndUpstreamServiceId(UUID workspaceId, UUID upstreamId);
    List<ServiceDependency> findByWorkspaceIdAndDownstreamServiceId(UUID workspaceId, UUID downstreamId);
}
