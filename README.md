# aws-parameter-store-plugin
A Jenkins plugin to populate environment variables from [AWS Parameter Store](https://docs.aws.amazon.com/systems-manager/latest/userguide/systems-manager-paramstore.html).

## Overview
This plugin collects parameters from the AWS Parameter Store and sets them as environment variables for a build.

Each AWS Parameter Store parameter name is converted to uppercase and any non-numeric characters are converted to underscores. For example, the parameter name `my-param_1` with value `my-value-1` would become the environment variable `MY_PARAM_1=my-value-1`.

This plugin is compatible with [AWS Parameter Store Hierarchies](https://docs.aws.amazon.com/systems-manager/latest/userguide/sysman-paramstore-working.html). A **path** and **recursive** indicator can be specified to pass to the [GetParametersByPath](https://docs.aws.amazon.com/systems-manager/latest/APIReference/API_GetParametersByPath.html) API call. The **path** is removed from the variable name. A parameter name `/service/param1` with value `value1` and a **path** of `/service/` would become `PARAM1=value1`.

  * **String** parameter values are unchanged
  * **SecureString** parameter values are decrypted
  * **StringList** parameter values are set as comma-separated values

## Configuration

The plugin is configured on each build configuration page:

![Screenshot](images/screenshot-1.png)

This has the following fields:

  * **AWS Credentials** - the id of credentials added via the [AWS Credentials Plugin](https://plugins.jenkins.io/aws-credentials)
  * **AWS Region Name** - the region name to search for parameters (defaults to `us-east-1`)
  * **Path** - the hierarchy for the parameters
  * **Recursive** - whether to retrieve all parameters within a hierarchy
