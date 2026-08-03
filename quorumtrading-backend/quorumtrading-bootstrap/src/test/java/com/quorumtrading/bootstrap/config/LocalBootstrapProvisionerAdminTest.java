package com.quorumtrading.bootstrap.config;

import com.quorumtrading.application.port.out.UserRepository;
import com.quorumtrading.application.usecase.AuthenticateUserUseCase;
import com.quorumtrading.application.usecase.apitoken.CreateApiTokenUseCase;
import com.quorumtrading.application.usecase.result.ApiTokenCreateResult;
import com.quorumtrading.model.entity.User;
import com.quorumtrading.model.enums.OAuthProvider;
import com.quorumtrading.model.valueobject.ApiTokenId;
import com.quorumtrading.model.valueobject.UserId;
import io.quarkus.runtime.StartupEvent;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The self-host owner is provisioned as an administrator.
 *
 * <p>Without this they are created as a plain USER and locked out of every admin screen on their
 * own machine. Feature flags are the sharp case: the whole point of storing them in the database
 * is that a self-hoster can switch features on for themselves, which they cannot do if the screen
 * that does it is hidden from them.</p>
 *
 * <p>The alternative — exempting standalone instances inside each authorization check — was tried
 * and removed. It was true at the service layer only, so the sidebar went on hiding the admin
 * section from exactly the user the exemption was written for. Promoting the row instead means
 * every layer agrees, because they were all already reading the same role.</p>
 */
@DisplayName("LocalBootstrapProvisioner owner promotion")
class LocalBootstrapProvisionerAdminTest {

    @TempDir
    Path tempDir;

    private AuthenticateUserUseCase authenticateUserUseCase;
    private UserRepository userRepository;
    private LocalBootstrapProvisioner provisioner;

    @BeforeEach
    void setUp() {
        authenticateUserUseCase = mock(AuthenticateUserUseCase.class);
        userRepository = mock(UserRepository.class);

        CreateApiTokenUseCase createApiTokenUseCase = mock(CreateApiTokenUseCase.class);
        when(createApiTokenUseCase.execute(any())).thenReturn(new ApiTokenCreateResult(
            ApiTokenId.generate(), "qt_test-token", "local", "qt_test", Instant.now(), null));

        provisioner = new LocalBootstrapProvisioner();
        provisioner.enabled = true;
        provisioner.tokenFilePath = tempDir.resolve("mcp-token").toString();
        provisioner.userEmail = "local@quorumtrading.local";
        provisioner.userName = "Local";
        provisioner.providerSub = "dev-login-local-sub";
        provisioner.mcpUrl = "http://localhost:8085/mcp";
        provisioner.authenticateUserUseCase = authenticateUserUseCase;
        provisioner.userRepository = userRepository;
        provisioner.createApiTokenUseCase = createApiTokenUseCase;
        // Keeps the LLM banner and auto-select paths out of this test; both short-circuit on it.
        provisioner.llmSelectionEnabled = false;
        provisioner.llmAutoSelectEnabled = false;
    }

    private User owner() {
        return User.create(UserId.generate(), "local@quorumtrading.local", "Local", null,
            OAuthProvider.GOOGLE);
    }

    @Test
    @DisplayName("a freshly created owner is promoted to ADMIN and saved")
    void promotesNewOwner() {
        User user = owner();
        assertThat(user.isAdmin()).as("users are created as plain USER").isFalse();
        when(authenticateUserUseCase.execute(any())).thenReturn(user);

        provisioner.onStart(new StartupEvent());

        assertThat(user.isAdmin()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("an existing owner left as USER by an older build is promoted on the next boot")
    void promotesExistingOwner() {
        // The reason promotion runs on every boot rather than only at creation: installs
        // provisioned before this existed already have a USER row, and would stay locked out.
        User user = owner();
        when(authenticateUserUseCase.execute(any())).thenReturn(user);

        provisioner.onStart(new StartupEvent());

        assertThat(user.isAdmin()).isTrue();
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("an owner who is already an administrator is not written again")
    void doesNotRewriteAnExistingAdmin() {
        User user = owner();
        user.promoteToAdmin();
        when(authenticateUserUseCase.execute(any())).thenReturn(user);

        provisioner.onStart(new StartupEvent());

        assertThat(user.isAdmin()).isTrue();
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("nothing is provisioned, or promoted, when auto-provisioning is off")
    void doesNothingWhenDisabled() {
        // This is the guard that keeps promotion off the hosted product: the provisioner only runs
        // where quorumtrading.local.auto-provision.enabled is set, which is the self-host edition.
        provisioner.enabled = false;

        provisioner.onStart(new StartupEvent());

        verify(authenticateUserUseCase, never()).execute(any());
        verify(userRepository, never()).save(any());
    }
}
