# AWS Bedrock Integration Setup

This project uses AWS Bedrock with Claude for inferring API path templates when the trie cache misses.

## Prerequisites

1. **AWS Account** with Bedrock access
2. **AWS Credentials** configured on your system
3. **Bedrock Model Access** - Request access to Claude models in the AWS Console

## AWS Credentials Setup

### Option 1: AWS CLI Configuration
```bash
aws configure
```

### Option 2: Environment Variables
```bash
export AWS_ACCESS_KEY_ID="your-access-key"
export AWS_SECRET_ACCESS_KEY="your-secret-key"
export AWS_DEFAULT_REGION="us-east-1"
```

### Option 3: IAM Role (for EC2/Lambda/ECS)
The application will automatically use the instance/task IAM role.

## Required IAM Permissions

The AWS credentials must have the following IAM permissions:

```json
{
  "Version": "2012-10-17",
  "Statement": [
    {
      "Effect": "Allow",
      "Action": [
        "bedrock:InvokeModel"
      ],
      "Resource": [
        "arn:aws:bedrock:*::foundation-model/anthropic.claude-*"
      ]
    }
  ]
}
```

## Model Configuration

The default configuration uses:
- **Region**: `us-east-1`
- **Model ID**: `anthropic.claude-3-5-sonnet-20241022-v2:0`

To use a different model or region:

```java
Region region = Region.US_WEST_2;
String modelId = "anthropic.claude-3-5-sonnet-20241022-v2:0";

BedrockTemplateInferenceService llmService =
    new BedrockTemplateInferenceService(region, modelId);
```

## Available Claude Models on Bedrock

- `anthropic.claude-3-5-sonnet-20241022-v2:0` (Recommended)
- `anthropic.claude-3-sonnet-20240229-v1:0`
- `anthropic.claude-3-haiku-20240307-v1:0`

## Usage Example

```java
// Create components
PathTemplateTrie trie = new PathTemplateTrie();
BedrockTemplateInferenceService llmService = new BedrockTemplateInferenceService();
PathResolverService resolver = new PathResolverService(trie, llmService);

// Resolve a path (uses cache or LLM)
MatchResult result = resolver.resolve("/users/jack/posts/123");

// Result will contain:
// - template: "/users/{name}/posts/{id}"
// - params: {"name": "jack", "id": "123"}
```

## How It Works

1. **Cache Hit**: Path is found in trie → return immediately (O(k) where k = segments)
2. **Cache Miss**: Path not in trie → invoke Claude on Bedrock
3. **LLM Inference**: Claude analyzes the path and returns a parameterized template
4. **Cache Update**: Insert the inferred template into the trie
5. **Future Lookups**: Subsequent similar paths hit the cache (no LLM call)

## Cost Optimization

The system is designed to minimize LLM costs:
- First lookup for a pattern invokes the LLM
- All subsequent similar lookups hit the O(k) trie cache
- Over time, LLM call frequency approaches zero as the cache converges

### Example Cost Analysis
- Claude Sonnet on Bedrock: ~$3 per 1M input tokens
- Average path inference: ~200 tokens
- Cost per inference: ~$0.0006
- After caching: $0 per lookup

## Testing Without AWS

To test the trie functionality without AWS credentials:

```bash
mvn clean compile exec:java -Dexec.mainClass="salt.security.Main"
```

To test with Bedrock integration:

```bash
mvn clean compile exec:java -Dexec.mainClass="salt.security.Main" -Dexec.args="llm"
```

## Troubleshooting

### Error: "Unable to load credentials"
- Ensure AWS credentials are properly configured
- Check that your credentials have Bedrock permissions

### Error: "ResourceNotFoundException"
- The model ID may not be available in your region
- Request access to Claude models in the AWS Bedrock console

### Error: "AccessDeniedException"
- Your IAM role/user lacks `bedrock:InvokeModel` permission
- Add the required IAM policy shown above

## Production Considerations

1. **Caching Strategy**: Consider persisting the trie to disk/database for faster startup
2. **Monitoring**: Track cache hit rate and LLM invocation frequency
3. **Fallback**: Implement graceful degradation if Bedrock is unavailable
4. **Rate Limiting**: Bedrock has rate limits - implement exponential backoff
5. **Cost Alerts**: Set up AWS billing alerts for Bedrock usage
