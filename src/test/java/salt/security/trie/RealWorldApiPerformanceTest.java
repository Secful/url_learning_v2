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

        for (ApiPathPattern pattern : API_PATTERNS) {
            apiCounts.merge(pattern.apiName, 1, Integer::sum);
        }

        logger.info("API Distribution:");
        apiCounts.entrySet().stream()
            .sorted(Map.Entry.<String, Integer>comparingByValue().reversed())
            .forEach(entry ->
                logger.info("  " + entry.getKey() + ": " + entry.getValue() + " patterns"));

        logger.info("\nTotal API patterns: " + API_PATTERNS.size());
        logger.info("Unique APIs: " + apiCounts.size());

        // Verify diversity
        assertTrue(apiCounts.size() >= 5, "Should have at least 5 different API types");
        assertTrue(API_PATTERNS.size() >= 50, "Should have at least 50 path patterns");
    }
}
