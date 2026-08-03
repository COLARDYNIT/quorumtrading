package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.adapter.rest.dto.response.InvestmentAllocationResponse;
import com.quorumtrading.adapter.rest.exception.BadRequestException;
import com.quorumtrading.adapter.rest.exception.NotFoundException;
import com.quorumtrading.adapter.rest.security.CurrentUserProvider;
import com.quorumtrading.application.usecase.investmentplan.GetInvestmentAllocationUseCase;
import com.quorumtrading.application.usecase.investmentplan.ListInvestmentAllocationsUseCase;
import com.quorumtrading.model.valueobject.InvestmentAllocationId;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Path("/api/v1/investment-allocations")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Authenticated
@Tag(name = "Investment Allocations", description = "Cross-plan audit trail for recurring investments")
public class InvestmentAllocationResource {

    @Inject ListInvestmentAllocationsUseCase listUseCase;
    @Inject GetInvestmentAllocationUseCase getUseCase;
    @Inject CurrentUserProvider currentUser;

    @GET
    @Operation(summary = "List allocations across all the user's plans",
            description = "Newest first. Drives the global DCA history view.")
    public List<InvestmentAllocationResponse> list(@QueryParam("limit") @DefaultValue("100") int limit) {
        UserId userId = currentUser.getCurrentUserId();
        return listUseCase.forUser(userId, limit).stream()
                .map(InvestmentAllocationResponse::fromDomain)
                .toList();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get one allocation by ID")
    public InvestmentAllocationResponse get(@PathParam("id") String id) {
        UserId userId = currentUser.getCurrentUserId();
        InvestmentAllocationId allocationId;
        try {
            allocationId = new InvestmentAllocationId(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid allocation ID format: " + id);
        }
        try {
            return InvestmentAllocationResponse.fromDomain(getUseCase.getOwnedBy(allocationId, userId));
        } catch (NoSuchElementException | SecurityException e) {
            throw new NotFoundException("Allocation not found: " + id);
        }
    }
}
