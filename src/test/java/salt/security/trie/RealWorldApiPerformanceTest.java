package salt.security.trie;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import salt.security.PathResolverService;
import salt.security.llm.BedrockTemplateInferenceService;
import salt.security.llm.TemplateInferenceService;

import java.util.*;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Real-world API performance test based on actual OpenAPI specifications.
 *
 * This test simulates a production scenario where:
 * 1. An empty trie encounters diverse API traffic
 * 2. LLM (Claude) infers templates on cache misses
 * 3. Templates are cached in the trie
 * 4. Subsequent requests benefit from sub-microsecond lookups
 *
 * APIs tested:
 * - Petstore API (classic REST API example)
 * - Star Trek API (STAPI - 25+ resource types)
 * - USPTO API (government data API)
 * - ReadMe API (documentation platform)
 *
 * Goal: Populate ~1,000 unique templates and measure warm cache performance
 * with 1,000,000 lookups.
 */
class RealWorldApiPerformanceTest {
    private static final Logger logger = Logger.getLogger(RealWorldApiPerformanceTest.class.getName());

    private static PathResolverService resolver;
    private static PathTemplateTrie trie;

    // Real-world API path templates from various OpenAPI specs
    private static final List<ApiPathPattern> API_PATTERNS = new ArrayList<>();

    /**
     * Represents an API path pattern with example concrete paths
     */
    private static class ApiPathPattern {
        String template;
        List<String> examples;
        String apiName;

        ApiPathPattern(String template, String apiName, String... examples) {
            this.template = template;
            this.apiName = apiName;
            this.examples = Arrays.asList(examples);
        }
    }

    @BeforeAll
    static void setupRealWorldApis() {
        logger.info("\n" + "=".repeat(80));
        logger.info("REAL-WORLD API PERFORMANCE TEST");
        logger.info("=".repeat(80));

        // Initialize PathResolverService with Bedrock LLM
        TemplateInferenceService llmService = new BedrockTemplateInferenceService();
        trie = new PathTemplateTrie();
        resolver = new PathResolverService(trie, llmService);

        // Initialize API patterns from real OpenAPI specs
        initializePetstoreApi();
        initializeStarTrekApi();
        initializeUsptoApi();
        initializeReadMeApi();
        initializeGitHubStyleApi();
        initializeStripeStyleApi();
        initializeSlackStyleApi();
        initializeTwilioStyleApi();
        initializeGitLabApi();
        initializeKubernetesApi();
        initializeDockerHubApi();

        logger.info("Initialized " + API_PATTERNS.size() + " API path patterns");
    }

    /**
     * Classic Petstore API (OpenAPI example)
     */
    private static void initializePetstoreApi() {
        API_PATTERNS.add(new ApiPathPattern(
            "/pets/{id}",
            "Petstore",
            "/pets/123",
            "/pets/456",
            "/pets/789"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/pets/{id}/photos",
            "Petstore",
            "/pets/123/photos",
            "/pets/456/photos"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/store/order/{orderId}",
            "Petstore",
            "/store/order/98765",
            "/store/order/12345"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/user/{username}",
            "Petstore",
            "/user/john_doe",
            "/user/jane_smith"
        ));
    }

    /**
     * Star Trek API (STAPI) - 25+ resource types
     */
    private static void initializeStarTrekApi() {
        String[] resources = {
            "animal", "astronomicalObject", "book", "bookCollection", "bookSeries",
            "character", "comics", "comicCollection", "comicSeries", "comicStrip",
            "company", "conflict", "element", "episode", "food", "literature",
            "location", "magazine", "magazineSeries", "material", "medicalCondition",
            "movie", "occupation", "organization", "performer", "planet", "season",
            "series", "soundtrack", "spacecraft", "species", "staff", "technology",
            "title", "tradingCard", "tradingCardDeck", "tradingCardSet", "weapon"
        };

        for (String resource : resources) {
            // Note: Query parameters are NOT part of path template matching
            // The trie only handles path segments
            API_PATTERNS.add(new ApiPathPattern(
                "/api/v1/rest/" + resource + "/{uid}",
                "STAPI",
                "/api/v1/rest/" + resource + "/CHMA0000000001",
                "/api/v1/rest/" + resource + "/CHMA0000000002",
                "/api/v1/rest/" + resource + "/CHMA0000000003"
            ));

            API_PATTERNS.add(new ApiPathPattern(
                "/api/v1/rest/" + resource + "/search",
                "STAPI",
                "/api/v1/rest/" + resource + "/search"
            ));
        }
    }

    /**
     * USPTO Data API (government API)
     * Modified to include record IDs as parameters
     */
    private static void initializeUsptoApi() {
        String[] datasets = {"oa_citations", "patent_grants", "patent_applications"};
        String[] versions = {"v1", "v2", "v3"};

        for (String dataset : datasets) {
            for (String version : versions) {
                API_PATTERNS.add(new ApiPathPattern(
                    "/" + dataset + "/" + version + "/fields/{fieldId}",
                    "USPTO",
                    "/" + dataset + "/" + version + "/fields/inventor_name",
                    "/" + dataset + "/" + version + "/fields/patent_number"
                ));

                API_PATTERNS.add(new ApiPathPattern(
                    "/" + dataset + "/" + version + "/records/{recordId}",
                    "USPTO",
                    "/" + dataset + "/" + version + "/records/US12345678",
                    "/" + dataset + "/" + version + "/records/US87654321"
                ));
            }
        }
    }

    /**
     * ReadMe API (documentation platform)
     */
    private static void initializeReadMeApi() {
        API_PATTERNS.add(new ApiPathPattern(
            "/api-registry/{uuid}",
            "ReadMe",
            "/api-registry/550e8400-e29b-41d4-a716-446655440000",
            "/api-registry/6ba7b810-9dad-11d1-80b4-00c04fd430c8"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api-specification/{id}",
            "ReadMe",
            "/api-specification/64e7a294e854ff2eb3550075",
            "/api-specification/507f1f77bcf86cd799439011"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/categories/{slug}",
            "ReadMe",
            "/categories/getting-started",
            "/categories/api-reference"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/categories/{slug}/docs",
            "ReadMe",
            "/categories/getting-started/docs",
            "/categories/api-reference/docs"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/docs/{slug}",
            "ReadMe",
            "/docs/introduction",
            "/docs/authentication"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/changelogs/{slug}",
            "ReadMe",
            "/changelogs/version-2-0-released",
            "/changelogs/new-api-endpoints"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/version/{versionId}",
            "ReadMe",
            "/version/1.0.0",
            "/version/2.1.3"
        ));
    }

    /**
     * GitHub-style API patterns
     */
    private static void initializeGitHubStyleApi() {
        API_PATTERNS.add(new ApiPathPattern(
            "/repos/{owner}/{repo}",
            "GitHub",
            "/repos/facebook/react",
            "/repos/microsoft/typescript"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/repos/{owner}/{repo}/issues/{number}",
            "GitHub",
            "/repos/facebook/react/issues/12345",
            "/repos/microsoft/typescript/issues/67890"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/repos/{owner}/{repo}/pulls/{number}",
            "GitHub",
            "/repos/facebook/react/pulls/999",
            "/repos/microsoft/typescript/pulls/1234"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/users/{username}",
            "GitHub",
            "/users/torvalds",
            "/users/gvanrossum"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/orgs/{org}/repos",
            "GitHub",
            "/orgs/google/repos",
            "/orgs/netflix/repos"
        ));
    }

    /**
     * Stripe-style API patterns
     */
    private static void initializeStripeStyleApi() {
        API_PATTERNS.add(new ApiPathPattern(
            "/v1/customers/{id}",
            "Stripe",
            "/v1/customers/cus_123456789",
            "/v1/customers/cus_987654321"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v1/charges/{id}",
            "Stripe",
            "/v1/charges/ch_123456789",
            "/v1/charges/ch_987654321"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v1/subscriptions/{id}",
            "Stripe",
            "/v1/subscriptions/sub_123456789",
            "/v1/subscriptions/sub_987654321"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v1/payment_intents/{id}",
            "Stripe",
            "/v1/payment_intents/pi_123456789",
            "/v1/payment_intents/pi_987654321"
        ));
    }

    /**
     * Slack-style API patterns
     * All paths now have placeholders for proper testing
     */
    private static void initializeSlackStyleApi() {
        API_PATTERNS.add(new ApiPathPattern(
            "/api/conversations/{conversationId}/history",
            "Slack",
            "/api/conversations/C1234567890/history",
            "/api/conversations/C9876543210/history"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/users/{userId}/info",
            "Slack",
            "/api/users/U1234567890/info",
            "/api/users/U9876543210/info"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/channels/{channelId}/messages",
            "Slack",
            "/api/channels/C1234567890/messages",
            "/api/channels/C9876543210/messages"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/channels/{channelId}/messages/{messageId}",
            "Slack",
            "/api/channels/C1234567890/messages/1234567890.123456",
            "/api/channels/C9876543210/messages/9876543210.987654"
        ));
    }

    /**
     * Twilio-style API patterns
     */
    private static void initializeTwilioStyleApi() {
        API_PATTERNS.add(new ApiPathPattern(
            "/2010-04-01/Accounts/{AccountSid}/Messages/{MessageSid}",
            "Twilio",
            "/2010-04-01/Accounts/AC1234567890abcdef/Messages/SM1234567890abcdef",
            "/2010-04-01/Accounts/AC9876543210fedcba/Messages/SM9876543210fedcba"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/2010-04-01/Accounts/{AccountSid}/Calls/{CallSid}",
            "Twilio",
            "/2010-04-01/Accounts/AC1234567890abcdef/Calls/CA1234567890abcdef"
        ));
    }

    /**
     * GitLab API patterns (from real GitLab OpenAPI spec)
     * Covers groups, projects, access control, CI/CD, packages, etc.
     */
    private static void initializeGitLabApi() {
        // Group Management
        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}",
            "GitLab",
            "/api/v4/groups/5",
            "/api/v4/groups/my-group"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/subgroups",
            "GitLab",
            "/api/v4/groups/12/subgroups",
            "/api/v4/groups/45/subgroups"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/descendant_groups",
            "GitLab",
            "/api/v4/groups/8/descendant_groups"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/projects",
            "GitLab",
            "/api/v4/groups/10/projects",
            "/api/v4/groups/99/projects"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/projects/shared",
            "GitLab",
            "/api/v4/groups/15/projects/shared"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/projects/{project_id}",
            "GitLab",
            "/api/v4/groups/7/projects/42"
        ));

        // Access Control
        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/access_requests",
            "GitLab",
            "/api/v4/groups/3/access_requests"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/access_requests/{user_id}/approve",
            "GitLab",
            "/api/v4/groups/11/access_requests/22/approve"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/access_requests/{user_id}",
            "GitLab",
            "/api/v4/groups/6/access_requests/18"
        ));

        // Badges & Customization
        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/badges",
            "GitLab",
            "/api/v4/groups/9/badges"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/badges/{badge_id}",
            "GitLab",
            "/api/v4/groups/9/badges/14"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/custom_attributes",
            "GitLab",
            "/api/v4/groups/21/custom_attributes"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/custom_attributes/{key}",
            "GitLab",
            "/api/v4/groups/21/custom_attributes/department"
        ));

        // Award Emoji
        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/epics/{epic_iid}/award_emoji",
            "GitLab",
            "/api/v4/groups/5/epics/2/award_emoji"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/epics/{epic_iid}/award_emoji/{award_id}",
            "GitLab",
            "/api/v4/groups/5/epics/2/award_emoji/7"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/epics/{epic_iid}/notes/{note_id}/award_emoji",
            "GitLab",
            "/api/v4/groups/5/epics/2/notes/3/award_emoji"
        ));

        // Audit & Security
        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/audit_events",
            "GitLab",
            "/api/v4/groups/4/audit_events"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/audit_events/{audit_event_id}",
            "GitLab",
            "/api/v4/groups/4/audit_events/156"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/saml_users",
            "GitLab",
            "/api/v4/groups/8/saml_users"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/provisioned_users",
            "GitLab",
            "/api/v4/groups/19/provisioned_users"
        ));

        // SSH & Keys
        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/ssh_certificates",
            "GitLab",
            "/api/v4/groups/13/ssh_certificates"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/ssh_certificates/{ssh_certificates_id}",
            "GitLab",
            "/api/v4/groups/13/ssh_certificates/5"
        ));

        // Runners & CI/CD
        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/runners",
            "GitLab",
            "/api/v4/groups/20/runners"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/runners/reset_registration_token",
            "GitLab",
            "/api/v4/groups/20/runners/reset_registration_token"
        ));

        // Deployment & Resources
        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/deploy_tokens",
            "GitLab",
            "/api/v4/groups/17/deploy_tokens"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/deploy_tokens/{token_id}",
            "GitLab",
            "/api/v4/groups/17/deploy_tokens/9"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/dependency_proxy/cache",
            "GitLab",
            "/api/v4/groups/25/dependency_proxy/cache"
        ));

        // Clusters & Infrastructure
        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/clusters",
            "GitLab",
            "/api/v4/groups/11/clusters"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/clusters/{cluster_id}",
            "GitLab",
            "/api/v4/groups/11/clusters/3"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/clusters/user",
            "GitLab",
            "/api/v4/groups/11/clusters/user"
        ));

        // Container Registry
        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/registry/repositories",
            "GitLab",
            "/api/v4/groups/14/registry/repositories"
        ));

        // Transfer & Organization
        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/transfer_locations",
            "GitLab",
            "/api/v4/groups/16/transfer_locations"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/transfer",
            "GitLab",
            "/api/v4/groups/16/transfer"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/transfer_to_organization",
            "GitLab",
            "/api/v4/groups/16/transfer_to_organization"
        ));

        // Sharing & Administration
        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/share",
            "GitLab",
            "/api/v4/groups/23/share"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/share/{group_id}",
            "GitLab",
            "/api/v4/groups/23/share/24"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/ldap_sync",
            "GitLab",
            "/api/v4/groups/27/ldap_sync"
        ));

        // Archive & Restoration
        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/archive",
            "GitLab",
            "/api/v4/groups/30/archive"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/unarchive",
            "GitLab",
            "/api/v4/groups/31/unarchive"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/restore",
            "GitLab",
            "/api/v4/groups/32/restore"
        ));

        // Avatar & Metadata
        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/groups/{id}/avatar",
            "GitLab",
            "/api/v4/groups/33/avatar"
        ));

        // Projects
        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/projects/{id}",
            "GitLab",
            "/api/v4/projects/42",
            "/api/v4/projects/123"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/projects/{id}/issues",
            "GitLab",
            "/api/v4/projects/42/issues",
            "/api/v4/projects/55/issues"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/projects/{id}/issues/{issue_iid}",
            "GitLab",
            "/api/v4/projects/42/issues/15",
            "/api/v4/projects/55/issues/27"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/projects/{id}/merge_requests",
            "GitLab",
            "/api/v4/projects/42/merge_requests"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/projects/{id}/merge_requests/{merge_request_iid}",
            "GitLab",
            "/api/v4/projects/42/merge_requests/99"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/projects/{id}/pipelines",
            "GitLab",
            "/api/v4/projects/42/pipelines"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/projects/{id}/pipelines/{pipeline_id}",
            "GitLab",
            "/api/v4/projects/42/pipelines/8765"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/projects/{id}/repository/commits/{sha}",
            "GitLab",
            "/api/v4/projects/42/repository/commits/a1b2c3d4e5f6"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/projects/{id}/repository/branches/{branch}",
            "GitLab",
            "/api/v4/projects/42/repository/branches/main",
            "/api/v4/projects/42/repository/branches/develop"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/projects/{id}/repository/tags/{tag_name}",
            "GitLab",
            "/api/v4/projects/42/repository/tags/v1.0.0",
            "/api/v4/projects/42/repository/tags/v2.3.1"
        ));

        // Users
        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/users/{id}",
            "GitLab",
            "/api/v4/users/100",
            "/api/v4/users/250"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v4/users/{id}/projects",
            "GitLab",
            "/api/v4/users/100/projects"
        ));
    }

    /**
     * Kubernetes API (v1.35) - 100 diverse K8s REST API endpoints
     * Covers Core API, Apps, Batch, Networking, RBAC, Storage, Autoscaling
     */
    private static void initializeKubernetesApi() {
        // Core API - Pods
        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/namespaces/{namespace}/pods/{name}",
            "Kubernetes",
            "/api/v1/namespaces/default/pods/nginx-7d8f4c9b5c-xk9m2",
            "/api/v1/namespaces/production/pods/redis-master-0",
            "/api/v1/namespaces/kube-system/pods/coredns-565d847f94-lmwkz"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/namespaces/{namespace}/pods/{name}/log",
            "Kubernetes",
            "/api/v1/namespaces/default/pods/nginx-7d8f4c9b5c-xk9m2/log",
            "/api/v1/namespaces/production/pods/api-server-89f7c6d-w5r2m/log"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/namespaces/{namespace}/pods/{name}/status",
            "Kubernetes",
            "/api/v1/namespaces/default/pods/nginx-7d8f4c9b5c-xk9m2/status",
            "/api/v1/namespaces/staging/pods/postgres-0/status"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/namespaces/{namespace}/pods/{name}/exec",
            "Kubernetes",
            "/api/v1/namespaces/default/pods/nginx-7d8f4c9b5c-xk9m2/exec",
            "/api/v1/namespaces/production/pods/debug-pod/exec"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/namespaces/{namespace}/pods/{name}/attach",
            "Kubernetes",
            "/api/v1/namespaces/default/pods/nginx-7d8f4c9b5c-xk9m2/attach"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/namespaces/{namespace}/pods/{name}/portforward",
            "Kubernetes",
            "/api/v1/namespaces/default/pods/nginx-7d8f4c9b5c-xk9m2/portforward"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/namespaces/{namespace}/pods",
            "Kubernetes",
            "/api/v1/namespaces/default/pods",
            "/api/v1/namespaces/production/pods",
            "/api/v1/namespaces/kube-system/pods"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/pods",
            "Kubernetes",
            "/api/v1/pods"
        ));

        // Core API - Services
        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/namespaces/{namespace}/services/{name}",
            "Kubernetes",
            "/api/v1/namespaces/default/services/kubernetes",
            "/api/v1/namespaces/production/services/frontend-service",
            "/api/v1/namespaces/staging/services/db-service"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/namespaces/{namespace}/services/{name}/status",
            "Kubernetes",
            "/api/v1/namespaces/default/services/kubernetes/status",
            "/api/v1/namespaces/production/services/load-balancer/status"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/namespaces/{namespace}/services",
            "Kubernetes",
            "/api/v1/namespaces/default/services",
            "/api/v1/namespaces/production/services"
        ));

        // Core API - ConfigMaps
        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/namespaces/{namespace}/configmaps/{name}",
            "Kubernetes",
            "/api/v1/namespaces/default/configmaps/app-config",
            "/api/v1/namespaces/production/configmaps/database-config",
            "/api/v1/namespaces/kube-system/configmaps/coredns"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/namespaces/{namespace}/configmaps",
            "Kubernetes",
            "/api/v1/namespaces/default/configmaps",
            "/api/v1/namespaces/production/configmaps"
        ));

        // Core API - Secrets
        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/namespaces/{namespace}/secrets/{name}",
            "Kubernetes",
            "/api/v1/namespaces/default/secrets/db-password",
            "/api/v1/namespaces/production/secrets/api-key",
            "/api/v1/namespaces/kube-system/secrets/default-token-7x9km"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/namespaces/{namespace}/secrets",
            "Kubernetes",
            "/api/v1/namespaces/default/secrets",
            "/api/v1/namespaces/production/secrets"
        ));

        // Core API - PersistentVolumeClaims
        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/namespaces/{namespace}/persistentvolumeclaims/{name}",
            "Kubernetes",
            "/api/v1/namespaces/default/persistentvolumeclaims/postgres-pvc",
            "/api/v1/namespaces/production/persistentvolumeclaims/data-volume"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/namespaces/{namespace}/persistentvolumeclaims/{name}/status",
            "Kubernetes",
            "/api/v1/namespaces/default/persistentvolumeclaims/postgres-pvc/status"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/namespaces/{namespace}/persistentvolumeclaims",
            "Kubernetes",
            "/api/v1/namespaces/default/persistentvolumeclaims",
            "/api/v1/namespaces/production/persistentvolumeclaims"
        ));

        // Core API - PersistentVolumes
        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/persistentvolumes/{name}",
            "Kubernetes",
            "/api/v1/persistentvolumes/pv-nfs-001",
            "/api/v1/persistentvolumes/pv-local-ssd"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/persistentvolumes/{name}/status",
            "Kubernetes",
            "/api/v1/persistentvolumes/pv-nfs-001/status"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/persistentvolumes",
            "Kubernetes",
            "/api/v1/persistentvolumes"
        ));

        // Core API - ServiceAccounts
        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/namespaces/{namespace}/serviceaccounts/{name}",
            "Kubernetes",
            "/api/v1/namespaces/default/serviceaccounts/default",
            "/api/v1/namespaces/kube-system/serviceaccounts/kube-proxy",
            "/api/v1/namespaces/production/serviceaccounts/app-sa"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/namespaces/{namespace}/serviceaccounts",
            "Kubernetes",
            "/api/v1/namespaces/default/serviceaccounts",
            "/api/v1/namespaces/production/serviceaccounts"
        ));

        // Core API - Nodes
        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/nodes/{name}",
            "Kubernetes",
            "/api/v1/nodes/master-node-01",
            "/api/v1/nodes/worker-node-02",
            "/api/v1/nodes/node-1"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/nodes/{name}/status",
            "Kubernetes",
            "/api/v1/nodes/master-node-01/status",
            "/api/v1/nodes/worker-node-02/status"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/nodes",
            "Kubernetes",
            "/api/v1/nodes"
        ));

        // Core API - Namespaces
        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/namespaces/{name}",
            "Kubernetes",
            "/api/v1/namespaces/default",
            "/api/v1/namespaces/production",
            "/api/v1/namespaces/kube-system",
            "/api/v1/namespaces/staging"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/namespaces/{name}/status",
            "Kubernetes",
            "/api/v1/namespaces/default/status",
            "/api/v1/namespaces/production/status"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/namespaces",
            "Kubernetes",
            "/api/v1/namespaces"
        ));

        // Core API - Endpoints
        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/namespaces/{namespace}/endpoints/{name}",
            "Kubernetes",
            "/api/v1/namespaces/default/endpoints/kubernetes",
            "/api/v1/namespaces/production/endpoints/frontend-service"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/namespaces/{namespace}/endpoints",
            "Kubernetes",
            "/api/v1/namespaces/default/endpoints",
            "/api/v1/namespaces/production/endpoints"
        ));

        // Core API - Events
        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/namespaces/{namespace}/events/{name}",
            "Kubernetes",
            "/api/v1/namespaces/default/events/nginx-deployment.17c4e6d8f5a2b3d4"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/namespaces/{namespace}/events",
            "Kubernetes",
            "/api/v1/namespaces/default/events",
            "/api/v1/namespaces/production/events"
        ));

        // Core API - ResourceQuotas
        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/namespaces/{namespace}/resourcequotas/{name}",
            "Kubernetes",
            "/api/v1/namespaces/production/resourcequotas/compute-quota"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/namespaces/{namespace}/resourcequotas",
            "Kubernetes",
            "/api/v1/namespaces/production/resourcequotas"
        ));

        // Core API - LimitRanges
        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/namespaces/{namespace}/limitranges/{name}",
            "Kubernetes",
            "/api/v1/namespaces/default/limitranges/default-limits"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/v1/namespaces/{namespace}/limitranges",
            "Kubernetes",
            "/api/v1/namespaces/default/limitranges"
        ));

        // Apps API - Deployments
        API_PATTERNS.add(new ApiPathPattern(
            "/apis/apps/v1/namespaces/{namespace}/deployments/{name}",
            "Kubernetes",
            "/apis/apps/v1/namespaces/default/deployments/nginx-deployment",
            "/apis/apps/v1/namespaces/production/deployments/frontend-deployment",
            "/apis/apps/v1/namespaces/staging/deployments/backend-api"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/apis/apps/v1/namespaces/{namespace}/deployments/{name}/status",
            "Kubernetes",
            "/apis/apps/v1/namespaces/default/deployments/nginx-deployment/status",
            "/apis/apps/v1/namespaces/production/deployments/frontend-deployment/status"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/apis/apps/v1/namespaces/{namespace}/deployments/{name}/scale",
            "Kubernetes",
            "/apis/apps/v1/namespaces/default/deployments/nginx-deployment/scale",
            "/apis/apps/v1/namespaces/production/deployments/backend-api/scale"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/apis/apps/v1/namespaces/{namespace}/deployments",
            "Kubernetes",
            "/apis/apps/v1/namespaces/default/deployments",
            "/apis/apps/v1/namespaces/production/deployments"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/apis/apps/v1/deployments",
            "Kubernetes",
            "/apis/apps/v1/deployments"
        ));

        // Apps API - StatefulSets
        API_PATTERNS.add(new ApiPathPattern(
            "/apis/apps/v1/namespaces/{namespace}/statefulsets/{name}",
            "Kubernetes",
            "/apis/apps/v1/namespaces/default/statefulsets/postgres",
            "/apis/apps/v1/namespaces/production/statefulsets/cassandra",
            "/apis/apps/v1/namespaces/staging/statefulsets/redis-cluster"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/apis/apps/v1/namespaces/{namespace}/statefulsets/{name}/status",
            "Kubernetes",
            "/apis/apps/v1/namespaces/default/statefulsets/postgres/status"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/apis/apps/v1/namespaces/{namespace}/statefulsets/{name}/scale",
            "Kubernetes",
            "/apis/apps/v1/namespaces/default/statefulsets/postgres/scale"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/apis/apps/v1/namespaces/{namespace}/statefulsets",
            "Kubernetes",
            "/apis/apps/v1/namespaces/default/statefulsets",
            "/apis/apps/v1/namespaces/production/statefulsets"
        ));

        // Apps API - DaemonSets
        API_PATTERNS.add(new ApiPathPattern(
            "/apis/apps/v1/namespaces/{namespace}/daemonsets/{name}",
            "Kubernetes",
            "/apis/apps/v1/namespaces/kube-system/daemonsets/kube-proxy",
            "/apis/apps/v1/namespaces/default/daemonsets/fluentd",
            "/apis/apps/v1/namespaces/monitoring/daemonsets/node-exporter"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/apis/apps/v1/namespaces/{namespace}/daemonsets/{name}/status",
            "Kubernetes",
            "/apis/apps/v1/namespaces/kube-system/daemonsets/kube-proxy/status"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/apis/apps/v1/namespaces/{namespace}/daemonsets",
            "Kubernetes",
            "/apis/apps/v1/namespaces/kube-system/daemonsets",
            "/apis/apps/v1/namespaces/monitoring/daemonsets"
        ));

        // Apps API - ReplicaSets
        API_PATTERNS.add(new ApiPathPattern(
            "/apis/apps/v1/namespaces/{namespace}/replicasets/{name}",
            "Kubernetes",
            "/apis/apps/v1/namespaces/default/replicasets/nginx-deployment-7d8f4c9b5c",
            "/apis/apps/v1/namespaces/production/replicasets/frontend-85c9f7b8d"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/apis/apps/v1/namespaces/{namespace}/replicasets/{name}/status",
            "Kubernetes",
            "/apis/apps/v1/namespaces/default/replicasets/nginx-deployment-7d8f4c9b5c/status"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/apis/apps/v1/namespaces/{namespace}/replicasets/{name}/scale",
            "Kubernetes",
            "/apis/apps/v1/namespaces/default/replicasets/nginx-deployment-7d8f4c9b5c/scale"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/apis/apps/v1/namespaces/{namespace}/replicasets",
            "Kubernetes",
            "/apis/apps/v1/namespaces/default/replicasets",
            "/apis/apps/v1/namespaces/production/replicasets"
        ));

        // Batch API - Jobs
        API_PATTERNS.add(new ApiPathPattern(
            "/apis/batch/v1/namespaces/{namespace}/jobs/{name}",
            "Kubernetes",
            "/apis/batch/v1/namespaces/default/jobs/data-migration",
            "/apis/batch/v1/namespaces/production/jobs/backup-job",
            "/apis/batch/v1/namespaces/staging/jobs/db-restore"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/apis/batch/v1/namespaces/{namespace}/jobs/{name}/status",
            "Kubernetes",
            "/apis/batch/v1/namespaces/default/jobs/data-migration/status"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/apis/batch/v1/namespaces/{namespace}/jobs",
            "Kubernetes",
            "/apis/batch/v1/namespaces/default/jobs",
            "/apis/batch/v1/namespaces/production/jobs"
        ));

        // Batch API - CronJobs
        API_PATTERNS.add(new ApiPathPattern(
            "/apis/batch/v1/namespaces/{namespace}/cronjobs/{name}",
            "Kubernetes",
            "/apis/batch/v1/namespaces/default/cronjobs/nightly-backup",
            "/apis/batch/v1/namespaces/production/cronjobs/daily-report",
            "/apis/batch/v1/namespaces/staging/cronjobs/cleanup-job"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/apis/batch/v1/namespaces/{namespace}/cronjobs/{name}/status",
            "Kubernetes",
            "/apis/batch/v1/namespaces/default/cronjobs/nightly-backup/status"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/apis/batch/v1/namespaces/{namespace}/cronjobs",
            "Kubernetes",
            "/apis/batch/v1/namespaces/default/cronjobs",
            "/apis/batch/v1/namespaces/production/cronjobs"
        ));

        // Networking API - Ingresses
        API_PATTERNS.add(new ApiPathPattern(
            "/apis/networking.k8s.io/v1/namespaces/{namespace}/ingresses/{name}",
            "Kubernetes",
            "/apis/networking.k8s.io/v1/namespaces/default/ingresses/main-ingress",
            "/apis/networking.k8s.io/v1/namespaces/production/ingresses/api-ingress"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/apis/networking.k8s.io/v1/namespaces/{namespace}/ingresses/{name}/status",
            "Kubernetes",
            "/apis/networking.k8s.io/v1/namespaces/default/ingresses/main-ingress/status"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/apis/networking.k8s.io/v1/namespaces/{namespace}/ingresses",
            "Kubernetes",
            "/apis/networking.k8s.io/v1/namespaces/default/ingresses",
            "/apis/networking.k8s.io/v1/namespaces/production/ingresses"
        ));

        // Networking API - NetworkPolicies
        API_PATTERNS.add(new ApiPathPattern(
            "/apis/networking.k8s.io/v1/namespaces/{namespace}/networkpolicies/{name}",
            "Kubernetes",
            "/apis/networking.k8s.io/v1/namespaces/default/networkpolicies/deny-all",
            "/apis/networking.k8s.io/v1/namespaces/production/networkpolicies/allow-frontend"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/apis/networking.k8s.io/v1/namespaces/{namespace}/networkpolicies",
            "Kubernetes",
            "/apis/networking.k8s.io/v1/namespaces/default/networkpolicies",
            "/apis/networking.k8s.io/v1/namespaces/production/networkpolicies"
        ));

        // RBAC API - Roles
        API_PATTERNS.add(new ApiPathPattern(
            "/apis/rbac.authorization.k8s.io/v1/namespaces/{namespace}/roles/{name}",
            "Kubernetes",
            "/apis/rbac.authorization.k8s.io/v1/namespaces/default/roles/pod-reader",
            "/apis/rbac.authorization.k8s.io/v1/namespaces/production/roles/deploy-manager"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/apis/rbac.authorization.k8s.io/v1/namespaces/{namespace}/roles",
            "Kubernetes",
            "/apis/rbac.authorization.k8s.io/v1/namespaces/default/roles",
            "/apis/rbac.authorization.k8s.io/v1/namespaces/production/roles"
        ));

        // RBAC API - RoleBindings
        API_PATTERNS.add(new ApiPathPattern(
            "/apis/rbac.authorization.k8s.io/v1/namespaces/{namespace}/rolebindings/{name}",
            "Kubernetes",
            "/apis/rbac.authorization.k8s.io/v1/namespaces/default/rolebindings/read-pods",
            "/apis/rbac.authorization.k8s.io/v1/namespaces/production/rolebindings/admin-binding"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/apis/rbac.authorization.k8s.io/v1/namespaces/{namespace}/rolebindings",
            "Kubernetes",
            "/apis/rbac.authorization.k8s.io/v1/namespaces/default/rolebindings",
            "/apis/rbac.authorization.k8s.io/v1/namespaces/production/rolebindings"
        ));

        // RBAC API - ClusterRoles
        API_PATTERNS.add(new ApiPathPattern(
            "/apis/rbac.authorization.k8s.io/v1/clusterroles/{name}",
            "Kubernetes",
            "/apis/rbac.authorization.k8s.io/v1/clusterroles/cluster-admin",
            "/apis/rbac.authorization.k8s.io/v1/clusterroles/view",
            "/apis/rbac.authorization.k8s.io/v1/clusterroles/edit"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/apis/rbac.authorization.k8s.io/v1/clusterroles",
            "Kubernetes",
            "/apis/rbac.authorization.k8s.io/v1/clusterroles"
        ));

        // RBAC API - ClusterRoleBindings
        API_PATTERNS.add(new ApiPathPattern(
            "/apis/rbac.authorization.k8s.io/v1/clusterrolebindings/{name}",
            "Kubernetes",
            "/apis/rbac.authorization.k8s.io/v1/clusterrolebindings/cluster-admin-binding",
            "/apis/rbac.authorization.k8s.io/v1/clusterrolebindings/system:node"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/apis/rbac.authorization.k8s.io/v1/clusterrolebindings",
            "Kubernetes",
            "/apis/rbac.authorization.k8s.io/v1/clusterrolebindings"
        ));

        // Storage API - StorageClasses
        API_PATTERNS.add(new ApiPathPattern(
            "/apis/storage.k8s.io/v1/storageclasses/{name}",
            "Kubernetes",
            "/apis/storage.k8s.io/v1/storageclasses/standard",
            "/apis/storage.k8s.io/v1/storageclasses/fast-ssd",
            "/apis/storage.k8s.io/v1/storageclasses/slow"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/apis/storage.k8s.io/v1/storageclasses",
            "Kubernetes",
            "/apis/storage.k8s.io/v1/storageclasses"
        ));

        // Storage API - VolumeAttachments
        API_PATTERNS.add(new ApiPathPattern(
            "/apis/storage.k8s.io/v1/volumeattachments/{name}",
            "Kubernetes",
            "/apis/storage.k8s.io/v1/volumeattachments/csi-vol-123456"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/apis/storage.k8s.io/v1/volumeattachments",
            "Kubernetes",
            "/apis/storage.k8s.io/v1/volumeattachments"
        ));

        // Autoscaling API - HorizontalPodAutoscalers (v2)
        API_PATTERNS.add(new ApiPathPattern(
            "/apis/autoscaling/v2/namespaces/{namespace}/horizontalpodautoscalers/{name}",
            "Kubernetes",
            "/apis/autoscaling/v2/namespaces/default/horizontalpodautoscalers/php-apache",
            "/apis/autoscaling/v2/namespaces/production/horizontalpodautoscalers/backend-hpa"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/apis/autoscaling/v2/namespaces/{namespace}/horizontalpodautoscalers/{name}/status",
            "Kubernetes",
            "/apis/autoscaling/v2/namespaces/default/horizontalpodautoscalers/php-apache/status"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/apis/autoscaling/v2/namespaces/{namespace}/horizontalpodautoscalers",
            "Kubernetes",
            "/apis/autoscaling/v2/namespaces/default/horizontalpodautoscalers",
            "/apis/autoscaling/v2/namespaces/production/horizontalpodautoscalers"
        ));

        // Certificate API - CertificateSigningRequests
        API_PATTERNS.add(new ApiPathPattern(
            "/apis/certificates.k8s.io/v1/certificatesigningrequests/{name}",
            "Kubernetes",
            "/apis/certificates.k8s.io/v1/certificatesigningrequests/user-123-csr"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/apis/certificates.k8s.io/v1/certificatesigningrequests/{name}/approval",
            "Kubernetes",
            "/apis/certificates.k8s.io/v1/certificatesigningrequests/user-123-csr/approval"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/apis/certificates.k8s.io/v1/certificatesigningrequests",
            "Kubernetes",
            "/apis/certificates.k8s.io/v1/certificatesigningrequests"
        ));

        // Policy API - PodDisruptionBudgets
        API_PATTERNS.add(new ApiPathPattern(
            "/apis/policy/v1/namespaces/{namespace}/poddisruptionbudgets/{name}",
            "Kubernetes",
            "/apis/policy/v1/namespaces/default/poddisruptionbudgets/zookeeper-pdb",
            "/apis/policy/v1/namespaces/production/poddisruptionbudgets/frontend-pdb"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/apis/policy/v1/namespaces/{namespace}/poddisruptionbudgets/{name}/status",
            "Kubernetes",
            "/apis/policy/v1/namespaces/default/poddisruptionbudgets/zookeeper-pdb/status"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/apis/policy/v1/namespaces/{namespace}/poddisruptionbudgets",
            "Kubernetes",
            "/apis/policy/v1/namespaces/default/poddisruptionbudgets",
            "/apis/policy/v1/namespaces/production/poddisruptionbudgets"
        ));

        // API Discovery
        API_PATTERNS.add(new ApiPathPattern(
            "/apis/{group}/{version}",
            "Kubernetes",
            "/apis/apps/v1",
            "/apis/batch/v1",
            "/apis/networking.k8s.io/v1"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/api/{version}",
            "Kubernetes",
            "/api/v1"
        ));
    }

    /**
     * Docker Hub API (v2) - 100 diverse Docker Hub REST API endpoints
     * Covers repositories, images, tags, users, organizations, webhooks, access tokens, audit logs
     */
    private static void initializeDockerHubApi() {
        // Docker Hub API v2 - Repositories
        API_PATTERNS.add(new ApiPathPattern(
            "/v2/repositories/{namespace}/{repository}",
            "DockerHub",
            "/v2/repositories/library/nginx",
            "/v2/repositories/library/ubuntu",
            "/v2/repositories/library/redis",
            "/v2/repositories/docker/getting-started"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/repositories/{namespace}/{repository}/tags",
            "DockerHub",
            "/v2/repositories/library/nginx/tags",
            "/v2/repositories/library/ubuntu/tags",
            "/v2/repositories/library/redis/tags"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/repositories/{namespace}/{repository}/tags/{tag}",
            "DockerHub",
            "/v2/repositories/library/nginx/tags/latest",
            "/v2/repositories/library/ubuntu/tags/22.04",
            "/v2/repositories/library/redis/tags/7.0-alpine",
            "/v2/repositories/library/postgres/tags/15.2"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/repositories/{namespace}/{repository}/tags/{tag}/images",
            "DockerHub",
            "/v2/repositories/library/nginx/tags/latest/images",
            "/v2/repositories/library/ubuntu/tags/22.04/images"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/repositories/{namespace}/{repository}/dockerfile",
            "DockerHub",
            "/v2/repositories/library/nginx/dockerfile",
            "/v2/repositories/library/ubuntu/dockerfile"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/repositories/{namespace}/{repository}/buildhistory",
            "DockerHub",
            "/v2/repositories/library/nginx/buildhistory",
            "/v2/repositories/docker/getting-started/buildhistory"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/repositories/{namespace}/{repository}/autobuild",
            "DockerHub",
            "/v2/repositories/myorg/myapp/autobuild",
            "/v2/repositories/company/backend/autobuild"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/repositories/{namespace}/{repository}/autobuild/tags",
            "DockerHub",
            "/v2/repositories/myorg/myapp/autobuild/tags"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/repositories/{namespace}/{repository}/autobuild/tags/{tag}",
            "DockerHub",
            "/v2/repositories/myorg/myapp/autobuild/tags/latest"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/repositories/{namespace}/{repository}/autobuild/trigger-url",
            "DockerHub",
            "/v2/repositories/myorg/myapp/autobuild/trigger-url"
        ));

        // Webhooks
        API_PATTERNS.add(new ApiPathPattern(
            "/v2/repositories/{namespace}/{repository}/webhooks",
            "DockerHub",
            "/v2/repositories/library/nginx/webhooks",
            "/v2/repositories/myorg/myapp/webhooks"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/repositories/{namespace}/{repository}/webhooks/{webhook_id}",
            "DockerHub",
            "/v2/repositories/myorg/myapp/webhooks/550e8400-e29b-41d4-a716-446655440000",
            "/v2/repositories/company/backend/webhooks/6ba7b810-9dad-11d1-80b4-00c04fd430c8"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/repositories/{namespace}/{repository}/webhooks/{webhook_id}/history",
            "DockerHub",
            "/v2/repositories/myorg/myapp/webhooks/550e8400-e29b-41d4-a716-446655440000/history"
        ));

        // Collaborators
        API_PATTERNS.add(new ApiPathPattern(
            "/v2/repositories/{namespace}/{repository}/collaborators",
            "DockerHub",
            "/v2/repositories/myorg/myapp/collaborators",
            "/v2/repositories/company/backend/collaborators"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/repositories/{namespace}/{repository}/collaborators/{username}",
            "DockerHub",
            "/v2/repositories/myorg/myapp/collaborators/johndoe",
            "/v2/repositories/company/backend/collaborators/janedoe"
        ));

        // Permissions
        API_PATTERNS.add(new ApiPathPattern(
            "/v2/repositories/{namespace}/{repository}/permissions",
            "DockerHub",
            "/v2/repositories/myorg/myapp/permissions",
            "/v2/repositories/company/backend/permissions"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/repositories/{namespace}/{repository}/permissions/{username}",
            "DockerHub",
            "/v2/repositories/myorg/myapp/permissions/johndoe"
        ));

        // Repository Groups
        API_PATTERNS.add(new ApiPathPattern(
            "/v2/repositories/{namespace}/{repository}/groups",
            "DockerHub",
            "/v2/repositories/myorg/myapp/groups"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/repositories/{namespace}/{repository}/groups/{group_id}",
            "DockerHub",
            "/v2/repositories/myorg/myapp/groups/12345"
        ));

        // Comments
        API_PATTERNS.add(new ApiPathPattern(
            "/v2/repositories/{namespace}/{repository}/comments",
            "DockerHub",
            "/v2/repositories/library/nginx/comments",
            "/v2/repositories/library/ubuntu/comments"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/repositories/{namespace}/{repository}/comments/{comment_id}",
            "DockerHub",
            "/v2/repositories/library/nginx/comments/98765"
        ));

        // Stars
        API_PATTERNS.add(new ApiPathPattern(
            "/v2/repositories/{namespace}/{repository}/stars",
            "DockerHub",
            "/v2/repositories/library/nginx/stars",
            "/v2/repositories/library/ubuntu/stars"
        ));

        // Users
        API_PATTERNS.add(new ApiPathPattern(
            "/v2/users/{username}",
            "DockerHub",
            "/v2/users/johndoe",
            "/v2/users/janedoe",
            "/v2/users/docker"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/users/{username}/repositories",
            "DockerHub",
            "/v2/users/johndoe/repositories",
            "/v2/users/janedoe/repositories"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/users/{username}/starred",
            "DockerHub",
            "/v2/users/johndoe/starred",
            "/v2/users/janedoe/starred"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/users/login",
            "DockerHub",
            "/v2/users/login"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/users/{username}/tokens",
            "DockerHub",
            "/v2/users/johndoe/tokens"
        ));

        // Organizations
        API_PATTERNS.add(new ApiPathPattern(
            "/v2/orgs/{orgname}",
            "DockerHub",
            "/v2/orgs/docker",
            "/v2/orgs/mycompany",
            "/v2/orgs/acmecorp"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/orgs/{orgname}/repositories",
            "DockerHub",
            "/v2/orgs/docker/repositories",
            "/v2/orgs/mycompany/repositories"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/orgs/{orgname}/members",
            "DockerHub",
            "/v2/orgs/docker/members",
            "/v2/orgs/mycompany/members"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/orgs/{orgname}/members/{username}",
            "DockerHub",
            "/v2/orgs/mycompany/members/johndoe",
            "/v2/orgs/acmecorp/members/janedoe"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/orgs/{orgname}/teams",
            "DockerHub",
            "/v2/orgs/docker/teams",
            "/v2/orgs/mycompany/teams"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/orgs/{orgname}/teams/{team_id}",
            "DockerHub",
            "/v2/orgs/mycompany/teams/12345",
            "/v2/orgs/acmecorp/teams/67890"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/orgs/{orgname}/teams/{team_id}/members",
            "DockerHub",
            "/v2/orgs/mycompany/teams/12345/members"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/orgs/{orgname}/teams/{team_id}/members/{username}",
            "DockerHub",
            "/v2/orgs/mycompany/teams/12345/members/johndoe"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/orgs/{orgname}/groups",
            "DockerHub",
            "/v2/orgs/docker/groups",
            "/v2/orgs/mycompany/groups"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/orgs/{orgname}/groups/{group_id}",
            "DockerHub",
            "/v2/orgs/mycompany/groups/12345"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/orgs/{orgname}/groups/{group_id}/members",
            "DockerHub",
            "/v2/orgs/mycompany/groups/12345/members"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/orgs/{orgname}/settings",
            "DockerHub",
            "/v2/orgs/docker/settings",
            "/v2/orgs/mycompany/settings"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/orgs/{orgname}/settings/registry-access",
            "DockerHub",
            "/v2/orgs/mycompany/settings/registry-access"
        ));

        // Namespaces
        API_PATTERNS.add(new ApiPathPattern(
            "/v2/namespaces/{namespace}",
            "DockerHub",
            "/v2/namespaces/library",
            "/v2/namespaces/docker",
            "/v2/namespaces/myorg"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/namespaces/{namespace}/repositories",
            "DockerHub",
            "/v2/namespaces/library/repositories",
            "/v2/namespaces/docker/repositories"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/namespaces/{namespace}/repositories/{repository}",
            "DockerHub",
            "/v2/namespaces/library/repositories/nginx",
            "/v2/namespaces/docker/repositories/getting-started"
        ));

        // Access Tokens (Personal Access Tokens)
        API_PATTERNS.add(new ApiPathPattern(
            "/v2/access-tokens",
            "DockerHub",
            "/v2/access-tokens"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/access-tokens/{token_uuid}",
            "DockerHub",
            "/v2/access-tokens/550e8400-e29b-41d4-a716-446655440000",
            "/v2/access-tokens/6ba7b810-9dad-11d1-80b4-00c04fd430c8"
        ));

        // Audit Logs
        API_PATTERNS.add(new ApiPathPattern(
            "/v2/auditlogs/{namespace}",
            "DockerHub",
            "/v2/auditlogs/myorg",
            "/v2/auditlogs/acmecorp"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/auditlogs/{namespace}/actions",
            "DockerHub",
            "/v2/auditlogs/myorg/actions"
        ));

        // Rate Limiting
        API_PATTERNS.add(new ApiPathPattern(
            "/v2/rate-limit",
            "DockerHub",
            "/v2/rate-limit"
        ));

        // Publisher
        API_PATTERNS.add(new ApiPathPattern(
            "/v2/publishers/{publisher_id}",
            "DockerHub",
            "/v2/publishers/docker-inc",
            "/v2/publishers/redhat"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/publishers/{publisher_id}/images",
            "DockerHub",
            "/v2/publishers/docker-inc/images"
        ));

        // Docker Registry API v2 - Manifests
        API_PATTERNS.add(new ApiPathPattern(
            "/v2/{namespace}/{repository}/manifests/{reference}",
            "DockerHub",
            "/v2/library/nginx/manifests/latest",
            "/v2/library/ubuntu/manifests/22.04",
            "/v2/library/redis/manifests/sha256:abcdef1234567890"
        ));

        // Docker Registry API v2 - Blobs
        API_PATTERNS.add(new ApiPathPattern(
            "/v2/{namespace}/{repository}/blobs/{digest}",
            "DockerHub",
            "/v2/library/nginx/blobs/sha256:1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef",
            "/v2/library/ubuntu/blobs/sha256:fedcba0987654321fedcba0987654321fedcba0987654321fedcba0987654321"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/{namespace}/{repository}/blobs/uploads",
            "DockerHub",
            "/v2/myorg/myapp/blobs/uploads",
            "/v2/company/backend/blobs/uploads"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/{namespace}/{repository}/blobs/uploads/{upload_uuid}",
            "DockerHub",
            "/v2/myorg/myapp/blobs/uploads/550e8400-e29b-41d4-a716-446655440000"
        ));

        // Docker Registry API v2 - Tags
        API_PATTERNS.add(new ApiPathPattern(
            "/v2/{namespace}/{repository}/tags/list",
            "DockerHub",
            "/v2/library/nginx/tags/list",
            "/v2/library/ubuntu/tags/list",
            "/v2/library/redis/tags/list"
        ));

        // Docker Registry API v2 - Catalog
        API_PATTERNS.add(new ApiPathPattern(
            "/v2/_catalog",
            "DockerHub",
            "/v2/_catalog"
        ));

        // Docker Registry API v2 - Referrers (OCI Distribution)
        API_PATTERNS.add(new ApiPathPattern(
            "/v2/{namespace}/{repository}/referrers/{digest}",
            "DockerHub",
            "/v2/library/nginx/referrers/sha256:1234567890abcdef1234567890abcdef1234567890abcdef1234567890abcdef"
        ));

        // Extensions - Vulnerability Scanning
        API_PATTERNS.add(new ApiPathPattern(
            "/v2/repositories/{namespace}/{repository}/tags/{tag}/scans",
            "DockerHub",
            "/v2/repositories/library/nginx/tags/latest/scans",
            "/v2/repositories/myorg/myapp/tags/v1.0.0/scans"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/repositories/{namespace}/{repository}/tags/{tag}/scans/{scan_id}",
            "DockerHub",
            "/v2/repositories/library/nginx/tags/latest/scans/12345"
        ));

        // Extensions - Usage Analytics
        API_PATTERNS.add(new ApiPathPattern(
            "/v2/repositories/{namespace}/{repository}/analytics",
            "DockerHub",
            "/v2/repositories/library/nginx/analytics",
            "/v2/repositories/myorg/myapp/analytics"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/repositories/{namespace}/{repository}/analytics/pulls",
            "DockerHub",
            "/v2/repositories/library/nginx/analytics/pulls"
        ));

        // Extensions - Content Trust (Notary)
        API_PATTERNS.add(new ApiPathPattern(
            "/v2/repositories/{namespace}/{repository}/signatures",
            "DockerHub",
            "/v2/repositories/library/nginx/signatures",
            "/v2/repositories/myorg/myapp/signatures"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/repositories/{namespace}/{repository}/signatures/{tag}",
            "DockerHub",
            "/v2/repositories/library/nginx/signatures/latest"
        ));

        // Extensions - Build Cache
        API_PATTERNS.add(new ApiPathPattern(
            "/v2/repositories/{namespace}/{repository}/buildcache",
            "DockerHub",
            "/v2/repositories/myorg/myapp/buildcache"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/repositories/{namespace}/{repository}/buildcache/{cache_id}",
            "DockerHub",
            "/v2/repositories/myorg/myapp/buildcache/550e8400-e29b-41d4-a716-446655440000"
        ));

        // Extensions - Subscriptions & Billing
        API_PATTERNS.add(new ApiPathPattern(
            "/v2/orgs/{orgname}/subscription",
            "DockerHub",
            "/v2/orgs/mycompany/subscription",
            "/v2/orgs/acmecorp/subscription"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/orgs/{orgname}/billing",
            "DockerHub",
            "/v2/orgs/mycompany/billing"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/orgs/{orgname}/billing/history",
            "DockerHub",
            "/v2/orgs/mycompany/billing/history"
        ));

        // Extensions - Notifications
        API_PATTERNS.add(new ApiPathPattern(
            "/v2/users/{username}/notifications",
            "DockerHub",
            "/v2/users/johndoe/notifications"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/users/{username}/notifications/{notification_id}",
            "DockerHub",
            "/v2/users/johndoe/notifications/98765"
        ));

        // Search
        API_PATTERNS.add(new ApiPathPattern(
            "/v2/search/repositories",
            "DockerHub",
            "/v2/search/repositories"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/search/images",
            "DockerHub",
            "/v2/search/images"
        ));

        // Extensions - Image Layers
        API_PATTERNS.add(new ApiPathPattern(
            "/v2/repositories/{namespace}/{repository}/tags/{tag}/layers",
            "DockerHub",
            "/v2/repositories/library/nginx/tags/latest/layers",
            "/v2/repositories/myorg/myapp/tags/v1.0.0/layers"
        ));

        API_PATTERNS.add(new ApiPathPattern(
            "/v2/repositories/{namespace}/{repository}/tags/{tag}/layers/{layer_id}",
            "DockerHub",
            "/v2/repositories/library/nginx/tags/latest/layers/sha256:abcdef123456"
        ));
    }

    @Test
    @DisplayName("Full cycle: Empty trie → LLM learning → Warm cache performance (1M lookups)")
    void testFullCycleWithRealWorldApis() {
        logger.info("\n" + "=".repeat(80));
        logger.info("PHASE 1: COLD START - Learning API Patterns via LLM");
        logger.info("=".repeat(80));

        List<String> concretePaths = new ArrayList<>();
        Set<String> learnedTemplates = new HashSet<>();

        // Track LLM learning phase with quality metrics
        long llmStartTime = System.nanoTime();
        int llmCallCount = 0;
        int llmSuccessCount = 0;
        int llmFailureCount = 0;
        int exactTemplateMatches = 0;
        int functionallyCorrectTemplates = 0;
        int incorrectTemplates = 0;

        Map<String, List<String>> templateMismatches = new HashMap<>();
        Map<String, Integer> apiSuccessRate = new HashMap<>();
        Map<String, Integer> apiTotalCalls = new HashMap<>();

        // Learn patterns from example paths (simulating real API traffic)
        for (ApiPathPattern pattern : API_PATTERNS) {
            String expectedTemplate = pattern.template;
            String apiName = pattern.apiName;

            for (String examplePath : pattern.examples) {
                concretePaths.add(examplePath);
                llmCallCount++;

                apiTotalCalls.merge(apiName, 1, Integer::sum);

                // First lookup triggers LLM (cache miss)
                logger.info("Learning: " + examplePath);
                logger.info("  Expected template: " + expectedTemplate);

                var result = resolver.resolve(examplePath);

                if (result != null) {
                    String inferredTemplate = result.getTemplate();
                    learnedTemplates.add(inferredTemplate);
                    llmSuccessCount++;

                    logger.info("  ✓ LLM returned: " + inferredTemplate);

                    // Check template accuracy
                    if (inferredTemplate.equals(expectedTemplate)) {
                        exactTemplateMatches++;
                        apiSuccessRate.merge(apiName, 1, Integer::sum);
                        logger.info("  ✓✓ EXACT MATCH!");
                    } else {
                        // Check if it's functionally correct (can match the same paths)
                        boolean functionallyCorrect = verifyTemplateFunctionallyCorrect(
                            inferredTemplate, expectedTemplate, pattern.examples
                        );

                        if (functionallyCorrect) {
                            functionallyCorrectTemplates++;
                            apiSuccessRate.merge(apiName, 1, Integer::sum);
                            logger.info("  ✓ Functionally correct (different naming)");
                            logger.info("    Expected: " + expectedTemplate);
                            logger.info("    Got:      " + inferredTemplate);
                        } else {
                            incorrectTemplates++;
                            logger.warning("  ✗ INCORRECT TEMPLATE");
                            logger.warning("    Expected: " + expectedTemplate);
                            logger.warning("    Got:      " + inferredTemplate);

                            templateMismatches.computeIfAbsent(apiName, k -> new ArrayList<>())
                                .add("Path: " + examplePath + "\n  Expected: " + expectedTemplate +
                                     "\n  Got: " + inferredTemplate);
                        }
                    }
                } else {
                    llmFailureCount++;
                    logger.warning("  ✗ LLM FAILED to infer template for: " + examplePath);
                }
            }
        }

        long llmEndTime = System.nanoTime();
        double llmPhaseDuration = (llmEndTime - llmStartTime) / 1_000_000_000.0;

        logger.info("\n" + "=".repeat(80));
        logger.info("LLM OUTPUT QUALITY ANALYSIS");
        logger.info("=".repeat(80));
        logger.info("Total LLM calls:              " + llmCallCount);
        logger.info("Successful inferences:        " + llmSuccessCount + " (" +
            String.format("%.1f%%", 100.0 * llmSuccessCount / llmCallCount) + ")");
        logger.info("Failed inferences:            " + llmFailureCount + " (" +
            String.format("%.1f%%", 100.0 * llmFailureCount / llmCallCount) + ")");

        logger.info("\nTemplate Accuracy:");
        logger.info("  Exact matches:              " + exactTemplateMatches + " (" +
            String.format("%.1f%%", 100.0 * exactTemplateMatches / llmSuccessCount) + ")");
        logger.info("  Functionally correct:       " + functionallyCorrectTemplates + " (" +
            String.format("%.1f%%", 100.0 * functionallyCorrectTemplates / llmSuccessCount) + ")");
        logger.info("  Incorrect:                  " + incorrectTemplates + " (" +
            String.format("%.1f%%", 100.0 * incorrectTemplates / llmSuccessCount) + ")");

        int totalCorrect = exactTemplateMatches + functionallyCorrectTemplates;
        logger.info("  Overall accuracy:           " + totalCorrect + "/" + llmSuccessCount + " (" +
            String.format("%.1f%%", 100.0 * totalCorrect / llmSuccessCount) + ")");

        logger.info("\nAccuracy by API:");
        apiTotalCalls.entrySet().stream()
            .sorted(Map.Entry.comparingByKey())
            .forEach(entry -> {
                String api = entry.getKey();
                int total = entry.getValue();
                int successes = apiSuccessRate.getOrDefault(api, 0);
                double accuracy = 100.0 * successes / total;
                logger.info(String.format("  %-15s %3d/%3d  (%.1f%%)",
                    api + ":", successes, total, accuracy));
            });

        if (!templateMismatches.isEmpty()) {
            logger.info("\nTemplate Mismatches:");
            templateMismatches.forEach((api, mismatches) -> {
                logger.warning("  " + api + ":");
                mismatches.forEach(m -> logger.warning("    " + m));
            });
        }

        logger.info("\n" + "=".repeat(80));
        logger.info("LEARNING PHASE TIMING");
        logger.info("=".repeat(80));
        logger.info("Unique templates learned:     " + learnedTemplates.size());
        logger.info("Total learning time:          " + String.format("%.2f", llmPhaseDuration) + " seconds");
        logger.info("Avg time per LLM call:        " + String.format("%.2f", llmPhaseDuration / llmCallCount) + " seconds");

        // Verify we learned a substantial number of templates
        assertTrue(learnedTemplates.size() >= 45,
            "Should learn at least 45 unique templates, got: " + learnedTemplates.size());

        logger.info("\n" + "=".repeat(80));
        logger.info("PHASE 2: WARM CACHE - Performance Measurement (1,000,000 lookups)");
        logger.info("=".repeat(80));

        // Generate 1M random lookups using learned patterns
        Random random = new Random(42); // Fixed seed for reproducibility
        List<String> testPaths = new ArrayList<>();

        for (int i = 0; i < 1_000_000; i++) {
            String path = concretePaths.get(random.nextInt(concretePaths.size()));
            testPaths.add(path);
        }

        logger.info("Generated 1,000,000 test paths for warm cache benchmark");

        // Warm-up phase
        logger.info("Warming up JIT compiler...");
        for (int i = 0; i < 10_000; i++) {
            trie.lookup(testPaths.get(i));
        }

        // Actual benchmark
        logger.info("Starting benchmark...");
        long benchmarkStart = System.nanoTime();

        int successCount = 0;
        for (String path : testPaths) {
            MatchResult result = trie.lookup(path);
            if (result != null) {
                successCount++;
            }
        }

        long benchmarkEnd = System.nanoTime();
        long totalNanos = benchmarkEnd - benchmarkStart;

        double totalSeconds = totalNanos / 1_000_000_000.0;
        double avgNanos = (double) totalNanos / testPaths.size();
        double avgMicros = avgNanos / 1_000.0;
        long throughput = (long) (testPaths.size() / totalSeconds);

        logger.info("\n" + "=".repeat(80));
        logger.info("WARM CACHE PERFORMANCE RESULTS");
        logger.info("=".repeat(80));
        logger.info("Total lookups:       1,000,000");
        logger.info("Successful matches:  " + successCount + " (" +
            String.format("%.2f%%", 100.0 * successCount / testPaths.size()) + ")");
        logger.info("Total time:          " + String.format("%.3f", totalSeconds) + " seconds");
        logger.info("Average lookup time: " + String.format("%.2f", avgMicros) + " µs (" +
            String.format("%.0f", avgNanos) + " ns)");
        logger.info("Throughput:          " + String.format("%,d", throughput) + " lookups/second");

        double speedupVsLlm = (llmPhaseDuration / llmCallCount) / (avgNanos / 1_000_000_000.0);
        logger.info("Speedup vs LLM:      " + String.format("%.0f", speedupVsLlm) + "x faster");

        // Compare to cold cache
        logger.info("\n" + "=".repeat(80));
        logger.info("COLD vs WARM CACHE COMPARISON");
        logger.info("=".repeat(80));
        logger.info("Cold cache (LLM):    ~" + String.format("%.2f", llmPhaseDuration / llmCallCount) + " seconds per lookup");
        logger.info("Warm cache (Trie):   " + String.format("%.2f", avgMicros) + " µs per lookup");
        logger.info("Improvement:         " + String.format("%.0f", speedupVsLlm) + "x faster");

        // Assertions
        assertTrue(successCount > 950_000,
            "Should match >95% of lookups, got: " + successCount);
        assertTrue(avgMicros < 2.0,
            "Average lookup should be <2µs, got: " + String.format("%.2f", avgMicros) + "µs");
        assertTrue(throughput > 500_000,
            "Throughput should exceed 500K lookups/sec, got: " + throughput);

        logger.info("\n" + "=".repeat(80));
        logger.info("TEST PASSED ✓");
        logger.info("=".repeat(80));
        logger.info("LLM Accuracy:        " + totalCorrect + "/" + llmSuccessCount + " (" +
            String.format("%.1f%%", 100.0 * totalCorrect / llmSuccessCount) + ")");
        logger.info("Cache Success Rate:  " + successCount + "/" + testPaths.size() + " (" +
            String.format("%.2f%%", 100.0 * successCount / testPaths.size()) + ")");
    }

    /**
     * Verifies if an inferred template is functionally correct even if parameter names differ.
     * Example: /users/{id} and /users/{userId} are functionally equivalent.
     */
    private boolean verifyTemplateFunctionallyCorrect(String inferred, String expected, List<String> testPaths) {
        // Check if both templates have the same number of segments
        String[] inferredParts = inferred.split("/");
        String[] expectedParts = expected.split("/");

        if (inferredParts.length != expectedParts.length) {
            return false;
        }

        // Check segment by segment
        for (int i = 0; i < inferredParts.length; i++) {
            String inferredPart = inferredParts[i];
            String expectedPart = expectedParts[i];

            // CRITICAL: Detect multiple parameters in a single segment (invalid!)
            // Example: {countryCode}{number} is INVALID - should be rejected
            long inferredParamCount = inferredPart.chars().filter(ch -> ch == '{').count();
            if (inferredParamCount > 1) {
                logger.warning("  ✗ INVALID: Multiple parameters in single segment: " + inferredPart);
                return false;
            }

            boolean inferredIsWildcard = inferredPart.startsWith("{") && inferredPart.endsWith("}");
            boolean expectedIsWildcard = expectedPart.startsWith("{") && expectedPart.endsWith("}");

            // Both must be either wildcards or literals
            if (inferredIsWildcard != expectedIsWildcard) {
                return false;
            }

            // If both are literals, they must match exactly
            if (!inferredIsWildcard && !inferredPart.equals(expectedPart)) {
                return false;
            }
        }

        // Functionally equivalent - same structure, just different parameter names
        return true;
    }

    @Test
    @DisplayName("Verify trie contains diverse real-world API patterns")
    void testApiDiversity() {
        logger.info("\n=== API Pattern Diversity Test ===\n");

        Map<String, Integer> apiCounts = new HashMap<>();
        Map<String, Integer> apiExampleCounts = new HashMap<>();
        int totalExamples = 0;
        Set<String> uniquePaths = new HashSet<>();

        for (ApiPathPattern pattern : API_PATTERNS) {
            apiCounts.merge(pattern.apiName, 1, Integer::sum);
            int exampleCount = pattern.examples.size();
            apiExampleCounts.merge(pattern.apiName, exampleCount, Integer::sum);
            totalExamples += exampleCount;
            uniquePaths.addAll(pattern.examples);
        }

        logger.info("API Distribution (Patterns):");
        apiCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(entry ->
                logger.info("  " + entry.getKey() + ": " + entry.getValue() + " patterns"));

        logger.info("\nAPI Distribution (Concrete Example Paths):");
        apiExampleCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(entry ->
                logger.info("  " + entry.getKey() + ": " + entry.getValue() + " examples"));

        logger.info("\nTotal API patterns (templates): " + API_PATTERNS.size());
        logger.info("Total concrete example paths: " + totalExamples);
        logger.info("Unique concrete paths: " + uniquePaths.size());
        logger.info("Unique APIs: " + apiCounts.size());
        logger.info("Average examples per pattern: " + String.format("%.2f", (double) totalExamples / API_PATTERNS.size()));

        // Verify diversity
        assertTrue(apiCounts.size() >= 5, "Should have at least 5 different API types");
        assertTrue(API_PATTERNS.size() >= 50, "Should have at least 50 path patterns");
        assertTrue(uniquePaths.size() >= 100, "Should have at least 100 unique concrete paths");
    }
}
