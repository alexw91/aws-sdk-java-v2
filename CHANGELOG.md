 #### 👋 _Looking for changelogs for older versions? You can find them in the [changelogs](./changelogs) directory._
# __2.30.16__ __2025-02-07__
## __AWS Elemental MediaConvert__
  - ### Features
    - This release adds support for Animated GIF output, forced chroma sample positioning metadata, and Extensible Wave Container format

## __AWS Performance Insights__
  - ### Features
    - Adds documentation for dimension groups and dimensions to analyze locks for Database Insights.

## __AWS SDK for Java v2__
  - ### Features
    - Updated endpoint and partition metadata.

## __Amazon Elastic Container Registry__
  - ### Features
    - Adds support to handle the new basic scanning daily quota.

## __Amazon Elastic Kubernetes Service__
  - ### Features
    - Introduce versionStatus field to take place of status field in EKS DescribeClusterVersions API

## __Amazon Transcribe Service__
  - ### Features
    - This release adds support for the Clinical Note Template Customization feature for the AWS HealthScribe APIs within Amazon Transcribe.

## __Amazon Transcribe Streaming Service__
  - ### Features
    - This release adds support for the Clinical Note Template Customization feature for the AWS HealthScribe Streaming APIs within Amazon Transcribe.

# __2.30.15__ __2025-02-06__
## __AWS CRT HTTP Client__
  - ### Features
    - Allow users to configure connectionAcquisitionTimeout for AwsCrtHttpClient and AwsCrtAsyncHttpClient

## __AWS CloudFormation__
  - ### Features
    - We added 5 new stack refactoring APIs: CreateStackRefactor, ExecuteStackRefactor, ListStackRefactors, DescribeStackRefactor, ListStackRefactorActions.

## __AWS SDK for Java v2__
  - ### Features
    - Updated endpoint and partition metadata.

## __Amazon Connect Cases__
  - ### Features
    - This release adds the ability to conditionally require fields on a template. Check public documentation for more information.

## __Amazon Simple Storage Service__
  - ### Features
    - Updated list of the valid AWS Region values for the LocationConstraint parameter for general purpose buckets.

## __Cost Optimization Hub__
  - ### Features
    - This release enables AWS Cost Optimization Hub to show cost optimization recommendations for Amazon Auto Scaling Groups, including those with single and mixed instance types.

# __2.30.14__ __2025-02-05__
## __AWS SDK for Java v2__
  - ### Features
    - Updated endpoint and partition metadata.

  - ### Bugfixes
    - Fix an issue where the trailing checksum of a request body is not sent when the `Content-Length` header is explicitly set to `0`.

## __Amazon Relational Database Service__
  - ### Features
    - Documentation updates to clarify the description for the parameter AllocatedStorage for the DB cluster data type, the description for the parameter DeleteAutomatedBackups for the DeleteDBCluster API operation, and removing an outdated note for the CreateDBParameterGroup API operation.

## __Netty NIO HTTP Client__
  - ### Features
    - Fallback to prior knowledge if default client setting is ALPN and request has HTTP endpoint

# __2.30.13__ __2025-02-04__
## __AWS DataSync__
  - ### Features
    - Doc-only update to provide more information on using Kerberos authentication with SMB locations.

## __AWS Database Migration Service__
  - ### Features
    - Introduces TargetDataSettings with the TablePreparationMode option available for data migrations.

## __AWS Identity and Access Management__
  - ### Features
    - This release adds support for accepting encrypted SAML assertions. Customers can now configure their identity provider to encrypt the SAML assertions it sends to IAM.

## __AWS SDK for Java v2__
  - ### Features
    - Updated endpoint and partition metadata.

  - ### Bugfixes
    - Fixed an issue in the SDK where it unnecessarily buffers the entire content for streaming operations, causing OOM error. See [#5850](https://github.com/aws/aws-sdk-java-v2/issues/5850).

## __Amazon Neptune Graph__
  - ### Features
    - Added argument to `list-export` to filter by graph ID

## __Amazon SageMaker Service__
  - ### Features
    - IPv6 support for Hyperpod clusters

## __QBusiness__
  - ### Features
    - Adds functionality to enable/disable a new Q Business Chat orchestration feature. If enabled, Q Business can orchestrate over datasources and plugins without the need for customers to select specific chat modes.

# __2.30.12__ __2025-02-03__
## __AWS MediaTailor__
  - ### Features
    - Add support for CloudWatch Vended Logs which allows for delivery of customer logs to CloudWatch Logs, S3, or Firehose.

# __2.30.11__ __2025-01-31__
## __AWS CodeBuild__
  - ### Features
    - Added support for CodeBuild self-hosted Buildkite runner builds

## __AWS SDK for Java v2__
  - ### Bugfixes
    - Fixed an issue in SdkHttpUtils used in SdkHttpFullRequest where constructing with a query string consisting of a single "=" would throw an ArrayIndexOutOfBoundsException.

## __Agents for Amazon Bedrock Runtime__
  - ### Features
    - This change is to deprecate the existing citation field under RetrieveAndGenerateStream API response in lieu of GeneratedResponsePart and RetrievedReferences

## __Amazon Location Service Routes V2__
  - ### Features
    - The OptimizeWaypoints API now supports 50 waypoints per request (20 with constraints like AccessHours or AppointmentTime). It adds waypoint clustering via Clustering and ClusteringIndex for better optimization. Also, total distance validation is removed for greater flexibility.

## __Amazon Prometheus Service__
  - ### Features
    - Add support for sending metrics to cross account and CMCK AMP workspaces through RoleConfiguration on Create/Update Scraper.

## __Amazon Relational Database Service__
  - ### Features
    - Updates to Aurora MySQL and Aurora PostgreSQL API pages with instance log type in the create and modify DB Cluster.

## __Amazon S3__
  - ### Bugfixes
    - Stopped populating SessionMode by default for the SDK-created S3 express sessions. This value already matched the service-side default, and was already not sent by most SDK languages.

## __Amazon SageMaker Service__
  - ### Features
    - This release introduces a new valid value in InstanceType parameter: p5en.48xlarge, in ProductionVariant.

# __2.30.10__ __2025-01-30__
## __AWS MediaTailor__
  - ### Features
    - Adds options for configuring how MediaTailor conditions ads before inserting them into the content stream. Based on the new settings, MediaTailor will either transcode ads to match the content stream as it has in the past, or it will insert ads without first transcoding them.

## __AWS SDK for Java v2__
  - ### Features
    - Updated endpoint and partition metadata.

## __Agents for Amazon Bedrock Runtime__
  - ### Features
    - Add a 'reason' field to InternalServerException

## __Amazon AppStream__
  - ### Features
    - Add support for managing admin consent requirement on selected domains for OneDrive Storage Connectors in AppStream2.0.

## __Amazon Elastic Container Registry__
  - ### Features
    - Temporarily updating dualstack endpoint support

## __Amazon Elastic Container Registry Public__
  - ### Features
    - Temporarily updating dualstack endpoint support

## __Amazon S3 Tables__
  - ### Features
    - You can now use the CreateTable API operation to create tables with schemas by adding an optional metadata argument.

## __Amazon Verified Permissions__
  - ### Features
    - Adds Cedar JSON format support for entities and context data in authorization requests

## __QBusiness__
  - ### Features
    - Added APIs to manage QBusiness user subscriptions

# __2.30.9__ __2025-01-29__
## __AWS Billing and Cost Management Pricing Calculator__
  - ### Features
    - Added ConflictException error type in DeleteBillScenario, BatchDeleteBillScenarioCommitmentModification, BatchDeleteBillScenarioUsageModification, BatchUpdateBillScenarioUsageModification, and BatchUpdateBillScenarioCommitmentModification API operations.

## __AWS SDK for Java v2__
  - ### Features
    - Buffer input data from ContentStreamProvider in cases where content length is known.

## __Amazon Elastic Container Registry__
  - ### Features
    - Add support for Dualstack and Dualstack-with-FIPS Endpoints

## __Amazon Elastic Container Registry Public__
  - ### Features
    - Add support for Dualstack Endpoints

## __Amazon S3__
  - ### Bugfixes
    - Fixed an issue that could cause checksum mismatch errors when performing parallel uploads with the async S3 client and the SHA1 or SHA256 checksum algorithms selected.

## __Amazon Simple Storage Service__
  - ### Features
    - Change the type of MpuObjectSize in CompleteMultipartUploadRequest from int to long.

## __Amazon Transcribe Streaming Service__
  - ### Features
    - This release adds support for AWS HealthScribe Streaming APIs within Amazon Transcribe.

## __MailManager__
  - ### Features
    - This release includes a new feature for Amazon SES Mail Manager which allows customers to specify known addresses and domains and make use of those in traffic policies and rules actions to distinguish between known and unknown entries.

# __2.30.8__ __2025-01-28__
## __AWS AppSync__
  - ### Features
    - Add stash and outErrors to EvaluateCode/EvaluateMappingTemplate response

## __AWS DataSync__
  - ### Features
    - AWS DataSync now supports the Kerberos authentication protocol for SMB locations.

## __AWS SDK for Java v2__
  - ### Features
    - Buffer input data from ContentStreamProvider to avoid the need to reread the stream after calculating its length.

## __AWSDeadlineCloud__
  - ### Features
    - feature: Deadline: Add support for limiting the concurrent usage of external resources, like floating licenses, using limits and the ability to constrain the maximum number of workers that work on a job

## __Amazon Elastic Compute Cloud__
  - ### Features
    - This release changes the CreateFleet CLI and SDK's such that if you do not specify a client token, a randomly generated token is used for the request to ensure idempotency.

## __Amazon Kinesis Firehose__
  - ### Features
    - For AppendOnly streams, Firehose will automatically scale to match your throughput.

## __Timestream InfluxDB__
  - ### Features
    - Adds 'allocatedStorage' parameter to UpdateDbInstance API that allows increasing the database instance storage size and 'dbStorageType' parameter to UpdateDbInstance API that allows changing the storage type of the database instance

# __2.30.7__ __2025-01-27__
## __AWS Elemental MediaConvert__
  - ### Features
    - This release adds support for dynamic audio configuration and the ability to disable the deblocking filter for h265 encodes.

## __AWS IoT__
  - ### Features
    - Raised the documentParameters size limit to 30 KB for AWS IoT Device Management - Jobs.

## __AWS S3 Control__
  - ### Features
    - Minor fix to ARN validation for Lambda functions passed to S3 Batch Operations

## __AWS SDK for Java v2__
  - ### Features
    - Updated endpoint and partition metadata.

## __Agents for Amazon Bedrock__
  - ### Features
    - Add support for the prompt caching feature for Bedrock Prompt Management

# __2.30.6__ __2025-01-24__
## __AWS CloudTrail__
  - ### Features
    - This release introduces the SearchSampleQueries API that allows users to search for CloudTrail Lake sample queries.

## __AWS SSO OIDC__
  - ### Features
    - Fixed typos in the descriptions.

## __AWS Transfer Family__
  - ### Features
    - Added CustomDirectories as a new directory option for storing inbound AS2 messages, MDN files and Status files.

## __Amazon Elastic Kubernetes Service__
  - ### Features
    - Adds support for UpdateStrategies in EKS Managed Node Groups.

## __Amazon HealthLake__
  - ### Features
    - Added new authorization strategy value 'SMART_ON_FHIR' for CreateFHIRDatastore API to support Smart App 2.0

## __Amazon Simple Systems Manager (SSM)__
  - ### Features
    - Systems Manager doc-only update for January, 2025.

# __2.30.5__ __2025-01-23__
## __Amazon Elastic Compute Cloud__
  - ### Features
    - Added "future" allocation type for future dated capacity reservation

## __Netty NIO HTTP Client__
  - ### Features
    - Adds ALPN H2 support for Netty client

# __2.30.4__ __2025-01-22__
## __AWS CRT HTTP Client__
  - ### Bugfixes
    - Reuse connections that receive a 5xx service response.

## __AWS Elemental MediaLive__
  - ### Features
    - AWS Elemental MediaLive adds a new feature, ID3 segment tagging, in CMAF Ingest output groups. It allows customers to insert ID3 tags into every output segment, controlled by a newly added channel schedule action Id3SegmentTagging.

## __AWS Glue__
  - ### Features
    - Docs Update for timeout changes

## __AWS SDK for Java v2__
  - ### Features
    - Updated endpoint and partition metadata.

## __Agents for Amazon Bedrock Runtime__
  - ### Features
    - Adds multi-turn input support for an Agent node in an Amazon Bedrock Flow

## __Amazon WorkSpaces Thin Client__
  - ### Features
    - Rename WorkSpaces Web to WorkSpaces Secure Browser

## __Apache HTTP Client__
  - ### Bugfixes
    - Reuse connections that receive a 5xx service response.

## __Netty NIO HTTP Client__
  - ### Bugfixes
    - Reuse connections that receive a 5xx service response.

# __2.30.3__ __2025-01-21__
## __AWS Batch__
  - ### Features
    - Documentation-only update: clarified the description of the shareDecaySeconds parameter of the FairsharePolicy data type, clarified the description of the priority parameter of the JobQueueDetail data type.

## __AWS IoT SiteWise__
  - ### Features
    - AWS IoT SiteWise now supports ingestion and querying of Null (all data types) and NaN (double type) values of bad or uncertain data quality. New partial error handling prevents data loss during ingestion. Enabled by default for new customers; existing customers can opt-in.

## __AWS SDK for Java v2__
  - ### Features
    - Updated endpoint and partition metadata.

## __Amazon CloudWatch Logs__
  - ### Features
    - Documentation-only update to address doc errors

## __Amazon Cognito Identity Provider__
  - ### Features
    - corrects the dual-stack endpoint configuration for cognitoidp

## __Amazon Connect Service__
  - ### Features
    - Added DeleteContactFlowVersion API and the CAMPAIGN flow type

## __Amazon QuickSight__
  - ### Features
    - Added `DigitGroupingStyle` in ThousandsSeparator to allow grouping by `LAKH`( Indian Grouping system ) currency. Support LAKH and `CRORE` currency types in Column Formatting.

## __Amazon Simple Notification Service__
  - ### Features
    - This release adds support for the topic attribute FifoThroughputScope for SNS FIFO topics. For details, see the documentation history in the Amazon Simple Notification Service Developer Guide.

## __EMR Serverless__
  - ### Features
    - Increasing entryPoint in SparkSubmit to accept longer script paths. New limit is 4kb.

## __Emf Metric Logging Publisher__
  - ### Features
    - Added a new EmfMetricLoggingPublisher class that transforms SdkMetricCollection to emf format string and logs it, which will be automatically collected by cloudwatch.

# __2.30.2__ __2025-01-17__
## __AWS SDK for Java v2__
  - ### Features
    - Updated endpoint and partition metadata.

## __AWS User Notifications__
  - ### Features
    - Added support for Managed Notifications, integration with AWS Organization and added aggregation summaries for Aggregate Notifications

## __Amazon Bedrock Runtime__
  - ### Features
    - Allow hyphens in tool name for Converse and ConverseStream APIs

## __Amazon Detective__
  - ### Features
    - Doc only update for Detective documentation.

## __Amazon Elastic Compute Cloud__
  - ### Features
    - Release u7i-6tb.112xlarge, u7i-8tb.112xlarge, u7inh-32tb.480xlarge, p5e.48xlarge, p5en.48xlarge, f2.12xlarge, f2.48xlarge, trn2.48xlarge instance types.

## __Amazon SageMaker Service__
  - ### Features
    - Correction of docs for "Added support for ml.trn1.32xlarge instance type in Reserved Capacity Offering"

## __Amazon Simple Storage Service__
  - ### Bugfixes
    - Fixed contentLength mismatch issue thrown from putObject when multipartEnabled is true and a contentLength is provided in PutObjectRequest. See [#5807](https://github.com/aws/aws-sdk-java-v2/issues/5807)

# __2.30.1__ __2025-01-16__
## __AWS SDK for Java v2__
  - ### Features
    - Updated endpoint and partition metadata.

## __Amazon EC2 Container Service__
  - ### Features
    - The release addresses Amazon ECS documentation tickets.

## __Amazon SageMaker Service__
  - ### Features
    - Added support for ml.trn1.32xlarge instance type in Reserved Capacity Offering

# __2.30.0__ __2025-01-15__
## __AWS SDK for Java v2__
  - ### Features
    - Updated endpoint and partition metadata.

## __AWS SDK for Java v2 Migration Tool__
  - ### Bugfixes
    - Transform the getter methods on the service model classes that return SdkBytes to return ByteBuffer to be compatible with v1 style getters

## __Agents for Amazon Bedrock Runtime__
  - ### Features
    - Now supports streaming for inline agents.

## __Amazon API Gateway__
  - ### Features
    - Documentation updates for Amazon API Gateway

## __Amazon Cognito Identity__
  - ### Features
    - corrects the dual-stack endpoint configuration

## __Amazon Simple Email Service__
  - ### Features
    - This release introduces a new recommendation in Virtual Deliverability Manager Advisor, which detects elevated complaint rates for customer sending identities.

## __Amazon Simple Storage Service__
  - ### Features
    - S3 client behavior is updated to always calculate a checksum by default for operations that support it (such as PutObject or UploadPart), or require it (such as DeleteObjects). The checksum algorithm used by default is CRC32. The S3 client attempts to validate response checksums for all S3 API operations that support checksums. However, if the SDK has not implemented the specified checksum algorithm then this validation is skipped. See [Dev Guide](https://docs.aws.amazon.com/sdk-for-java/latest/developer-guide/s3-checksums.html) for more information
    - This change enhances integrity protections for new SDK requests to S3. S3 SDKs now support the CRC64NVME checksum algorithm, full object checksums for multipart S3 objects, and new default integrity protections for S3 requests.

## __Amazon WorkSpaces__
  - ### Features
    - Added GeneralPurpose.4xlarge & GeneralPurpose.8xlarge ComputeTypes.

## __Amazon WorkSpaces Thin Client__
  - ### Features
    - Mark type in MaintenanceWindow as required.

## __Partner Central Selling API__
  - ### Features
    - Add Tagging support for ResourceSnapshotJob resources

## __S3 Event Notification__
  - ### Bugfixes
    - add static modifier to fromJson(InputStream) method of S3EventNotification

## __Security Incident Response__
  - ### Features
    - Increase minimum length of Threat Actor IP 'userAgent' to 1.

