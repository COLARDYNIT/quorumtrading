package com.quorumtrading.adapter.rest.resource;

import com.quorumtrading.adapter.rest.dto.request.CreateInvestmentPlanRequest;
import com.quorumtrading.adapter.rest.dto.request.UpdateInvestmentPlanRequest;
import com.quorumtrading.adapter.rest.dto.response.InvestmentAllocationResponse;
import com.quorumtrading.adapter.rest.dto.response.InvestmentPlanResponse;
import com.quorumtrading.adapter.rest.exception.BadRequestException;
import com.quorumtrading.adapter.rest.exception.NotFoundException;
import com.quorumtrading.adapter.rest.security.CurrentUserProvider;
import com.quorumtrading.application.usecase.command.CreateInvestmentPlanCommand;
import com.quorumtrading.application.usecase.command.ExecuteInvestmentPlanCommand;
import com.quorumtrading.application.usecase.command.UpdateInvestmentPlanCommand;
import com.quorumtrading.application.usecase.investmentplan.CreateInvestmentPlanUseCase;
import com.quorumtrading.application.usecase.investmentplan.ExecuteInvestmentPlanUseCase;
import com.quorumtrading.application.usecase.investmentplan.GetInvestmentPlanUseCase;
import com.quorumtrading.application.usecase.investmentplan.ListInvestmentAllocationsUseCase;
import com.quorumtrading.application.usecase.investmentplan.ListInvestmentPlansUseCase;
import com.quorumtrading.application.usecase.investmentplan.PauseInvestmentPlanUseCase;
import com.quorumtrading.application.usecase.investmentplan.ResumeInvestmentPlanUseCase;
import com.quorumtrading.application.usecase.investmentplan.TerminateInvestmentPlanUseCase;
import com.quorumtrading.application.usecase.investmentplan.UpdateInvestmentPlanUseCase;
import com.quorumtrading.model.entity.InvestmentAllocation;
import com.quorumtrading.model.entity.InvestmentPlan;
import com.quorumtrading.model.enums.BudgetMode;
import com.quorumtrading.model.enums.InvestmentPlanStatus;
import com.quorumtrading.model.enums.PickerPolicy;
import com.quorumtrading.model.valueobject.BrokerAccountId;
import com.quorumtrading.model.valueobject.InvestmentPlanId;
import com.quorumtrading.model.valueobject.InvestmentPlanPreferences;
import com.quorumtrading.model.valueobject.InvestmentPlanPreferencesPatch;
import com.quorumtrading.model.valueobject.Money;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.security.Authenticated;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.core.UriInfo;
import org.eclipse.microprofile.openapi.annotations.Operation;
import org.eclipse.microprofile.openapi.annotations.tags.Tag;

import java.net.URI;
import java.time.Instant;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.UUID;

@Path("/api/v1/investment-plans")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
@ApplicationScoped
@Authenticated
@Tag(name = "Investment Plans", description = "Recurring investment plans (DCA-style)")
public class InvestmentPlanResource {

    /**
     * Whether the DCA scheduler will actually run plans here. Off by default in every
     * environment, so every plan response has to carry it — an ACTIVE plan with a
     * {@code nextExecutionAt} date and nothing to act on it is the one thing this feature must
     * never report silently.
     */
    @org.eclipse.microprofile.config.inject.ConfigProperty(
            name = "quorumtrading.scheduler.investments.enabled", defaultValue = "false")
    boolean automaticExecutionEnabled;

    @Inject CreateInvestmentPlanUseCase createUseCase;
    @Inject UpdateInvestmentPlanUseCase updateUseCase;
    @Inject PauseInvestmentPlanUseCase pauseUseCase;
    @Inject ResumeInvestmentPlanUseCase resumeUseCase;
    @Inject TerminateInvestmentPlanUseCase terminateUseCase;
    @Inject GetInvestmentPlanUseCase getUseCase;
    @Inject ListInvestmentPlansUseCase listUseCase;
    @Inject ListInvestmentAllocationsUseCase listAllocationsUseCase;
    @Inject ExecuteInvestmentPlanUseCase executeUseCase;
    @Inject CurrentUserProvider currentUser;

    @POST
    @Operation(summary = "Create a recurring investment plan")
    public Response create(CreateInvestmentPlanRequest request, @Context UriInfo uriInfo) {
        UserId userId = currentUser.getCurrentUserId();
        if (request == null || request.brokerAccountId() == null
                || request.budgetMode() == null || request.dayOfMonth() == null
                || request.pickerPolicy() == null) {
            throw new BadRequestException("brokerAccountId, budgetMode, dayOfMonth, and pickerPolicy are required");
        }

        BudgetMode mode = parseEnum(BudgetMode.class, request.budgetMode(), "budgetMode");
        PickerPolicy policy = parseEnum(PickerPolicy.class, request.pickerPolicy(), "pickerPolicy");
        BrokerAccountId brokerAccountId;
        try {
            brokerAccountId = new BrokerAccountId(UUID.fromString(request.brokerAccountId()));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid brokerAccountId: " + request.brokerAccountId());
        }

        Money amount = null;
        if (request.amount() != null) {
            Money.Currency currency = request.currency() == null || request.currency().isBlank()
                    ? Money.Currency.USD
                    : parseEnum(Money.Currency.class, request.currency(), "currency");
            amount = new Money(request.amount(), currency);
        }

        InvestmentPlanPreferences prefs = mapPreferences(request.preferences());

        InvestmentPlan plan;
        try {
            plan = createUseCase.execute(new CreateInvestmentPlanCommand(
                    userId, brokerAccountId, mode, amount, request.dayOfMonth(),
                    policy, prefs, request.confirmLive()));
        } catch (NoSuchElementException e) {
            throw new NotFoundException(e.getMessage());
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BadRequestException(e.getMessage());
        }

        URI location = uriInfo.getAbsolutePathBuilder()
                .path(plan.getId().value().toString())
                .build();
        return Response.created(location).entity(InvestmentPlanResponse.fromDomain(plan, automaticExecutionEnabled)).build();
    }

    @GET
    @Operation(summary = "List investment plans for the current user")
    public List<InvestmentPlanResponse> list(@QueryParam("status") String status) {
        UserId userId = currentUser.getCurrentUserId();
        Optional<InvestmentPlanStatus> filter = (status == null || status.isBlank())
                ? Optional.empty()
                : Optional.of(parseEnum(InvestmentPlanStatus.class, status, "status"));
        return listUseCase.listForUser(userId, filter).stream()
                .map(plan -> InvestmentPlanResponse.fromDomain(plan, automaticExecutionEnabled))
                .toList();
    }

    @GET
    @Path("/{id}")
    @Operation(summary = "Get an investment plan by ID")
    public InvestmentPlanResponse get(@PathParam("id") String id) {
        UserId userId = currentUser.getCurrentUserId();
        InvestmentPlanId planId = parsePlanId(id);
        try {
            return InvestmentPlanResponse.fromDomain(getUseCase.getOwnedBy(planId, userId), automaticExecutionEnabled);
        } catch (NoSuchElementException | SecurityException e) {
            throw new NotFoundException("Investment plan not found: " + id);
        }
    }

    @PUT
    @Path("/{id}")
    @Operation(summary = "Partially update an investment plan")
    public InvestmentPlanResponse update(@PathParam("id") String id, UpdateInvestmentPlanRequest request) {
        UserId userId = currentUser.getCurrentUserId();
        InvestmentPlanId planId = parsePlanId(id);

        Optional<BrokerAccountId> brokerId = Optional.empty();
        if (request != null && request.brokerAccountId() != null) {
            try {
                brokerId = Optional.of(new BrokerAccountId(UUID.fromString(request.brokerAccountId())));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Invalid brokerAccountId: " + request.brokerAccountId());
            }
        }
        Optional<BudgetMode> mode = request == null || request.budgetMode() == null
                ? Optional.empty() : Optional.of(parseEnum(BudgetMode.class, request.budgetMode(), "budgetMode"));
        Optional<Money> amount = Optional.empty();
        if (request != null && request.amount() != null) {
            Money.Currency currency = request.currency() == null || request.currency().isBlank()
                    ? Money.Currency.USD
                    : parseEnum(Money.Currency.class, request.currency(), "currency");
            amount = Optional.of(new Money(request.amount(), currency));
        }
        Optional<Integer> day = request == null || request.dayOfMonth() == null
                ? Optional.empty() : Optional.of(request.dayOfMonth());
        Optional<InvestmentPlanPreferencesPatch> prefs = (request != null && request.preferences() != null)
                ? Optional.of(mapPreferencesPatch(request.preferences()))
                : Optional.empty();

        try {
            InvestmentPlan updated = updateUseCase.execute(new UpdateInvestmentPlanCommand(
                    planId, userId, brokerId, mode, amount, day, prefs));
            return InvestmentPlanResponse.fromDomain(updated, automaticExecutionEnabled);
        } catch (NoSuchElementException | SecurityException e) {
            throw new NotFoundException("Investment plan not found: " + id);
        } catch (IllegalArgumentException | IllegalStateException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @POST
    @Path("/{id}/pause")
    public InvestmentPlanResponse pause(@PathParam("id") String id) {
        UserId userId = currentUser.getCurrentUserId();
        InvestmentPlanId planId = parsePlanId(id);
        try {
            return InvestmentPlanResponse.fromDomain(pauseUseCase.pause(planId, userId), automaticExecutionEnabled);
        } catch (NoSuchElementException | SecurityException e) {
            throw new NotFoundException("Investment plan not found: " + id);
        } catch (IllegalStateException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @POST
    @Path("/{id}/resume")
    public InvestmentPlanResponse resume(@PathParam("id") String id) {
        UserId userId = currentUser.getCurrentUserId();
        InvestmentPlanId planId = parsePlanId(id);
        try {
            return InvestmentPlanResponse.fromDomain(resumeUseCase.resume(planId, userId), automaticExecutionEnabled);
        } catch (NoSuchElementException | SecurityException e) {
            throw new NotFoundException("Investment plan not found: " + id);
        } catch (IllegalStateException e) {
            throw new BadRequestException(e.getMessage());
        }
    }

    @DELETE
    @Path("/{id}")
    public Response terminate(@PathParam("id") String id) {
        UserId userId = currentUser.getCurrentUserId();
        InvestmentPlanId planId = parsePlanId(id);
        try {
            terminateUseCase.terminate(planId, userId);
            return Response.noContent().build();
        } catch (NoSuchElementException | SecurityException e) {
            throw new NotFoundException("Investment plan not found: " + id);
        }
    }

    @POST
    @Path("/{id}/execute")
    @Operation(summary = "Manually execute the plan now",
            description = "Runs one execution tick immediately. On instances where the DCA "
                    + "scheduler is enabled it also runs monthly on its own; the scheduler is "
                    + "OFF by default, and while it is off this endpoint is the only thing that "
                    + "will ever fund the plan. Check automaticExecutionEnabled on the plan.")
    public InvestmentAllocationResponse executeNow(@PathParam("id") String id) {
        UserId userId = currentUser.getCurrentUserId();
        InvestmentPlanId planId = parsePlanId(id);
        try {
            InvestmentAllocation allocation = executeUseCase.execute(new ExecuteInvestmentPlanCommand(
                    planId, userId, Instant.now(), true));
            return InvestmentAllocationResponse.fromDomain(allocation);
        } catch (NoSuchElementException | SecurityException e) {
            throw new NotFoundException("Investment plan not found: " + id);
        }
    }

    @GET
    @Path("/{id}/allocations")
    @Operation(summary = "List allocations for one plan, newest first")
    public List<InvestmentAllocationResponse> listAllocations(
            @PathParam("id") String id,
            @QueryParam("limit") @DefaultValue("50") int limit) {
        UserId userId = currentUser.getCurrentUserId();
        InvestmentPlanId planId = parsePlanId(id);
        try {
            return listAllocationsUseCase.forPlan(planId, userId, limit).stream()
                    .map(InvestmentAllocationResponse::fromDomain)
                    .toList();
        } catch (NoSuchElementException | SecurityException e) {
            throw new NotFoundException("Investment plan not found: " + id);
        }
    }

    private InvestmentPlanId parsePlanId(String id) {
        try {
            return new InvestmentPlanId(UUID.fromString(id));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Invalid plan ID format: " + id);
        }
    }

    private <E extends Enum<E>> E parseEnum(Class<E> enumClass, String value, String paramName) {
        try {
            return Enum.valueOf(enumClass, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Unknown " + paramName + ": " + value);
        }
    }

    /**
     * Maps a preferences payload on the <em>update</em> path, where a JSON field that was not sent
     * deserialises to null and must therefore mean "leave it alone" — not "clear it". Building a
     * whole {@link InvestmentPlanPreferences} here (as this endpoint used to) silently erased every
     * preference the caller did not restate, which changes which strategies the plan can fund.
     *
     * <p>To clear a ticker list, send it as {@code []}; to clear {@code riskTolerance}, send an
     * empty string.</p>
     */
    private InvestmentPlanPreferencesPatch mapPreferencesPatch(
            CreateInvestmentPlanRequest.PreferencesPayload p) {
        if (p == null) {
            return InvestmentPlanPreferencesPatch.EMPTY;
        }
        return new InvestmentPlanPreferencesPatch(
                Optional.ofNullable(p.riskTolerance()),
                Optional.ofNullable(p.allowedTickers()),
                Optional.ofNullable(p.blockedTickers()),
                Optional.ofNullable(p.maxStrategiesActive()),
                Optional.ofNullable(p.preferExistingHoldings()));
    }

    private InvestmentPlanPreferences mapPreferences(CreateInvestmentPlanRequest.PreferencesPayload p) {
        if (p == null) return InvestmentPlanPreferences.EMPTY;
        return new InvestmentPlanPreferences(
                p.riskTolerance(),
                p.allowedTickers() != null ? p.allowedTickers() : List.of(),
                p.blockedTickers() != null ? p.blockedTickers() : List.of(),
                p.maxStrategiesActive(),
                p.preferExistingHoldings() == null || p.preferExistingHoldings());
    }
}
