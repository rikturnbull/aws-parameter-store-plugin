/**
  * MIT License
  *
  * Copyright (c) 2018 Rik Turnbull
  *
  * Permission is hereby granted, free of charge, to any person obtaining a copy
  * of this software and associated documentation files (the "Software"), to deal
  * in the Software without restriction, including without limitation the rights
  * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
  * copies of the Software, and to permit persons to whom the Software is
  * furnished to do so, subject to the following conditions:
  *
  * The above copyright notice and this permission notice shall be included in all
  * copies or substantial portions of the Software.
  *
  * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
  * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
  * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
  * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
  * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
  * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
  * SOFTWARE.
  */
package hudson.plugins.awsparameterstore;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.amazonaws.ClientConfiguration;
import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;
import com.amazonaws.regions.Regions;
import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClient;
import com.amazonaws.services.simplesystemsmanagement.model.DescribeParametersRequest;
import com.amazonaws.services.simplesystemsmanagement.model.DescribeParametersResult;
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterResult;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersByPathRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersByPathResult;
import com.amazonaws.services.simplesystemsmanagement.model.Parameter;
import com.amazonaws.services.simplesystemsmanagement.model.ParameterMetadata;

import com.cloudbees.jenkins.plugins.awscredentials.AWSCredentialsHelper;
import com.cloudbees.jenkins.plugins.awscredentials.AmazonWebServicesCredentials;

import hudson.ProxyConfiguration;

import jenkins.model.Jenkins;

import org.apache.commons.lang.StringUtils;

/**
 * AWS Parameter Store client.
 *
 * @author Rik Turnbull
 *
 */
public class AwsParameterStoreService {
  private static final Logger LOGGER = Logger.getLogger(AwsParameterStoreService.class.getName());

  private AWSSimpleSystemsManagementClient client;

  private String credentialsId;
  private String regionName;

  /**
   * Creates a new {@link AwsParameterStoreService}.
   *
   * @param credentialsId  AWS credentials identifier
   * @param regionName     AWS region name
   */
  public AwsParameterStoreService(String credentialsId, String regionName) {
    this.credentialsId = credentialsId;
    this.regionName = regionName;
  }

  /**
   * Returns an {@link AWSSimpleSystemsManagementClient}.
   * @return {@link AWSSimpleSystemsManagementClient} singleton using the <code>credentialsId</code>
   * and <code>regionName</code>
   */
  private synchronized AWSSimpleSystemsManagementClient getAWSSimpleSystemsManagementClient() {
    if(client == null) {
      ClientConfiguration clientConfiguration = new ClientConfiguration();
      Jenkins jenkins = Jenkins.getInstance();
      if(jenkins != null) {
        ProxyConfiguration proxy = jenkins.proxy;
        if(proxy != null) {
          clientConfiguration.setProxyHost(proxy.name);
          clientConfiguration.setProxyPort(proxy.port);
          clientConfiguration.setProxyUsername(proxy.getUserName());
          clientConfiguration.setProxyPassword(proxy.getPassword());
        }
      }

      AmazonWebServicesCredentials credentials = getAWSCredentials(credentialsId);
      if(credentials == null) {
        client = new AWSSimpleSystemsManagementClient(clientConfiguration);
      } else {
        client = new AWSSimpleSystemsManagementClient(credentials, clientConfiguration);
      }
      client.setRegion(getRegion(regionName));
    }
    return client;
  }

  /**
   * Gets AWS region.
   *
   * @param regionName AWS region name
   * @return AWS region for <code>regionName</code> or US_EAST_1
   */
  private Region getRegion(String regionName) {
    Region region = RegionUtils.getRegion(regionName);
    if(region == null) {
      region = Region.getRegion(Regions.US_EAST_1);
    }
    return region;
  }

  /**
   * Gets AWS credentials.
   *
   * @param credentialsId Jenkins credentials identifier
   * @return AWS credentials for <code>credentialsId</code> that can be used
   * for AWS calls
   */
  private AmazonWebServicesCredentials getAWSCredentials(String credentialsId) {
    return AWSCredentialsHelper.getCredentials(credentialsId, Jenkins.getActiveInstance());
  }

  /**
   * Adds environment variables to <code>env</code>.
   *
   * @param env           environment variable map
   * @param path          hierarchy for the parameter
   * @param recursive     fetch all parameters within a hierarchy
   */
  public void buildEnvVars(Map<String, String> env, String path, Boolean recursive)  {
    if(StringUtils.isEmpty(path)) {
      buildEnvVarsWithParameters(env);
    } else {
      buildEnvVarsWithParametersByPath(env, path, recursive);
    }
  }

  /**
   * Adds environment variables to <code>env</code> using <code>describeParameters</code>.
   *
   * @param env           environment variable map
   */
  private void buildEnvVarsWithParameters(Map<String, String> env) {
    final AWSSimpleSystemsManagementClient client = getAWSSimpleSystemsManagementClient();
    final List<String> names = new ArrayList<String>();

    try {
      final DescribeParametersRequest describeParametersRequest = new DescribeParametersRequest().withMaxResults(1);

      do {
        final DescribeParametersResult describeParametersResult = client.describeParameters(describeParametersRequest);
        for(ParameterMetadata metadata : describeParametersResult.getParameters()) {
          names.add(metadata.getName());
        }
        describeParametersRequest.setNextToken(describeParametersResult.getNextToken());
      } while(describeParametersRequest.getNextToken() != null);
    } catch(Exception e) {
      LOGGER.log(Level.WARNING, "Cannot fetch parameters: " + e.getMessage(), e);
    }

    final GetParameterRequest getParameterRequest = new GetParameterRequest().withWithDecryption(true);
    for(String name : names) {
      getParameterRequest.setName(name);
      try {
        env.put(toEnvironmentVariable(name),
          client.getParameter(getParameterRequest).getParameter().getValue());
      } catch(Exception e) {
        LOGGER.log(Level.WARNING, "Cannot fetch parameter: \"" + name + "\"", e);
      }
    }
  }

  /**
   * Adds environment variables to <code>env</code> using <code>getParametersByPath</code>.
   *
   * @param env           environment variable map
   * @param path          hierarchy for the parameter
   * @param recursive     fetch all parameters within a hierarchy
   */
  public void buildEnvVarsWithParametersByPath(Map<String, String> env, String path, Boolean recursive)  {
    final AWSSimpleSystemsManagementClient client = getAWSSimpleSystemsManagementClient();

    try {
      final GetParametersByPathRequest getParametersByPathRequest =
         new GetParametersByPathRequest().withPath(path).
                                          withRecursive(recursive).
                                          withWithDecryption(true);
      do {
        final GetParametersByPathResult getParametersByPathResult = client.getParametersByPath(getParametersByPathRequest);
        for(Parameter parameter : getParametersByPathResult.getParameters()) {
          LOGGER.log(Level.INFO, parameter.toString());
          env.put(toEnvironmentVariable(parameter.getName()), parameter.getValue());
        }
        getParametersByPathRequest.setNextToken(getParametersByPathResult.getNextToken());
      } while(getParametersByPathRequest.getNextToken() != null);
    } catch(Exception e) {
      LOGGER.log(Level.WARNING, "Cannot fetch parameters by path: " + e.getMessage(), e);
    }
  }

  /**
   * Converts <code>name</code> to uppercase. All non alphanumeric characters are converted to underscores.
   *
   * @param name    parameter name
   */
  private String toEnvironmentVariable(String name) {
    StringBuffer environmentVariable = new StringBuffer();
    for(int i = name.lastIndexOf('/')+1; i < name.length(); i++) {
      char c = name.charAt(i);
      if(Character.isLetter(c)) {
        environmentVariable.append(Character.toUpperCase(c));
      } else if(Character.isDigit(c)) {
        environmentVariable.append(c);
      } else {
        environmentVariable.append('_');
      }
    }
    return environmentVariable.toString();
  }
}
