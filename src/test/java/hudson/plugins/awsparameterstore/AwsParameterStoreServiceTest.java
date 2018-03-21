/**
  * MIT License
  *
  * Copyright (c) 2017 Rik Turnbull
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

import com.amazonaws.services.simplesystemsmanagement.AWSSimpleSystemsManagementClient;
import com.amazonaws.services.simplesystemsmanagement.model.AWSSimpleSystemsManagementException;
import com.amazonaws.services.simplesystemsmanagement.model.DescribeParametersRequest;
import com.amazonaws.services.simplesystemsmanagement.model.DescribeParametersResult;
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetParameterResult;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersByPathRequest;
import com.amazonaws.services.simplesystemsmanagement.model.GetParametersByPathResult;
import com.amazonaws.services.simplesystemsmanagement.model.ParameterMetadata;

import com.cloudbees.jenkins.plugins.awscredentials.AWSCredentialsHelper;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jenkins.model.Jenkins;

import hudson.model.BuildableItem;
import hudson.model.Hudson;

import org.apache.commons.lang.StringUtils;
import org.hamcrest.Matcher;
import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mockito;
import org.mockito.ArgumentMatcher;
import org.powermock.api.easymock.PowerMock;
import org.powermock.api.easymock.annotation.Mock;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.modules.junit4.PowerMockRunnerDelegate;

/**
 * Run tests for {@link AwsParameterStoreService}.
 *
 * @author Rik Turnbull
 *
 */
@RunWith(PowerMockRunner.class)
@PowerMockRunnerDelegate(value = Parameterized.class)
@PrepareForTest({AwsParameterStoreService.class, Jenkins.class, AWSCredentialsHelper.class})
public class AwsParameterStoreServiceTest {

  private final static int NAME = 0;
  private final static int VALUE = 1;

  private final static String CREDENTIALS_AWS_ADMIN = "aws-admin";
  private final static String CREDENTIALS_AWS_NO_DESCRIBE = "aws-no-describe";
  private final static String CREDENTIALS_AWS_NO_GET = "aws-no-get";
  private final static String CREDENTIALS_AWS_NO_GETBYPATH = "aws-no-getbypath";

  private final static String REGION_NAME = "eu-west-1";

  @Parameter(0)
  public String[][] parameters;
  @Parameter(1)
  public String path;
  @Parameter(2)
  public Boolean recursive;
  @Parameter(3)
  public String[][] expected;
  @Parameter(4)
  public String credentialsId;

  @Parameters
  public static Collection<Object[]> data() {
    return Arrays.asList(
      new Object[][] {
        { /* normal */
          new String[][] { { "name1", "value1" }, { "name2", "value2" } },
          null,
          false,
          new String[][] { { "NAME1", "value1" }, { "NAME2", "value2" } },
          CREDENTIALS_AWS_ADMIN
        },
        { /* non-alphanumerics */
          new String[][] { { "*X()_test", "value1" }, { "123abCD", "value2" }, { "name3","value3" } },
          null,
          false,
          new String[][] { { "_X___TEST", "value1" }, { "123ABCD", "value2" }, { "NAME3", "value3" } },
          CREDENTIALS_AWS_ADMIN
        },
        { /* path test */
          new String[][] { { "/service/name1", "value1" }, { "/service/name2", "value2" }, {"/ignore/name3", "value3"} },
          "/service/",
          true,
          new String[][] { { "NAME1", "value1" }, { "NAME2", "value2" }, { "NAME3", null } },
          CREDENTIALS_AWS_ADMIN
        },
        { /* empty values */
          new String[][] { { "name1", "" }, { "name2", null }, { "name3","value3" } },
          null,
          false,
          new String[][] { { "NAME1", "" }, { "NAME2", null }, { "NAME3", "value3" } },
          CREDENTIALS_AWS_ADMIN
        },
        { /* no describe */
          new String[][] { { "name1", "value1" }, { "name2", "value2" } },
          null,
          false,
          new String[][] { { "NAME1", null }, { "NAME2", null } },
          CREDENTIALS_AWS_NO_DESCRIBE
        },
        { /* no get-parameter */
          new String[][] { { "name1", "value1" }, { "name2", "value2" } },
          null,
          false,
          new String[][] { { "NAME1", null }, { "NAME2", "value2" } },
          CREDENTIALS_AWS_NO_GET
        },
        { /* no get-parameter-by-path */
          new String[][] { { "name1", "value1" }, { "name2", "value2" } },
          "/service/",
          true,
          new String[][] { { "NAME1", null }, { "NAME2", null } },
          CREDENTIALS_AWS_NO_GETBYPATH
        }
      }
    );
  }

  /**
   * Set up mock classes.
   */
  @Before
  public void setUp() {
    mockAWSCredentialsHelper();
    mockAWSSimpleSystemsManagementClient();
    mockJenkins();
  }

  /**
   * Test that the getters returns values set in the constructor.
   */
  @Test
  public void testConstructor() {
    new AwsParameterStoreService(credentialsId, REGION_NAME);
  }

  /**
   * Tests the <code>describeImages</code> method returns images in a
   * sorted order.
   */
  @Test
  public void testBuildEnvVars() {
    Map<String, String> env = new HashMap<String, String>();
    AwsParameterStoreService awsParameterStoreService = new AwsParameterStoreService(credentialsId, REGION_NAME);
    awsParameterStoreService.buildEnvVars(env, path, recursive);
    for(int i = 0; i < expected.length; i++) {
      Assert.assertEquals(parameters[i][NAME], expected[i][VALUE], env.get(expected[i][NAME]));
    }
  }

  /**
   * Mocks the credential helper which requires a running Jenkins instance.
   */
  private void mockAWSCredentialsHelper() {
    AWSCredentialsHelper awsCredentialsHelper = PowerMockito.mock(AWSCredentialsHelper.class);
    PowerMockito.mockStatic(AWSCredentialsHelper.class);
    PowerMockito.when(AWSCredentialsHelper.getCredentials(Mockito.any(String.class), Mockito.any(hudson.model.ItemGroup.class))).thenReturn(null);
  }

  /**
   * Mocks the constructor, describeParameters(), getParameter() and getParametersByPath() methods of the <code>AwsSimpleSystemsManagementClient</code>.
   */
  private void mockAWSSimpleSystemsManagementClient() {
    AWSSimpleSystemsManagementClient awsSimpleSystemsManagementClient = PowerMockito.mock(AWSSimpleSystemsManagementClient.class);
    try {
      PowerMockito.whenNew(AWSSimpleSystemsManagementClient.class).withAnyArguments().thenReturn(awsSimpleSystemsManagementClient);
    } catch(Exception e) {
      Assert.fail("Unexpected exception: " + e.getMessage());
    }

    if(CREDENTIALS_AWS_NO_DESCRIBE.equals(credentialsId)) {
      PowerMockito.when(awsSimpleSystemsManagementClient.describeParameters(Mockito.any(DescribeParametersRequest.class))).thenThrow(
        new AWSSimpleSystemsManagementException("AccessDenied")
      );
    } else {
      PowerMockito.when(awsSimpleSystemsManagementClient.describeParameters(Mockito.any(DescribeParametersRequest.class))).thenReturn(
       new DescribeParametersResult().withParameters(mockParameterMetadata())
      );
    }

    if(CREDENTIALS_AWS_NO_GETBYPATH.equals(credentialsId)) {
      PowerMockito.when(awsSimpleSystemsManagementClient.getParametersByPath(Mockito.any(GetParametersByPathRequest.class))).thenThrow(
        new AWSSimpleSystemsManagementException("AccessDenied")
      );
    } else {
      PowerMockito.when(awsSimpleSystemsManagementClient.getParametersByPath(Mockito.any(GetParametersByPathRequest.class))).thenReturn(
        new GetParametersByPathResult().withParameters(mockParameters())
      );
    }

    for(int i = 0; i < parameters.length; i++) {
      if(CREDENTIALS_AWS_NO_GET.equals(credentialsId) && i == 0) {
        PowerMockito.when(awsSimpleSystemsManagementClient.getParameter(Mockito.argThat(new GetParameterRequestMatcher(parameters[i][NAME])))).thenThrow(
          new AWSSimpleSystemsManagementException("AcessDenied")
        );
      } else {
        PowerMockito.when(awsSimpleSystemsManagementClient.getParameter(Mockito.argThat(new GetParameterRequestMatcher(parameters[i][NAME])))).thenReturn(
          new GetParameterResult().withParameter(new com.amazonaws.services.simplesystemsmanagement.model.Parameter().withValue(parameters[i][VALUE]))
        );
      }
    }
  }

  /**
    * Generates <code>ParameterMetadata</code> return values.
    */
   private Collection<ParameterMetadata> mockParameterMetadata() {
     Collection<ParameterMetadata> parameterMetadata = new ArrayList<ParameterMetadata>();
     for(int i = 0; i < parameters.length; i++) {
       parameterMetadata.add(new ParameterMetadata().withName(parameters[i][NAME]));
     }
     return parameterMetadata;
   }

  /**
   * Generates <code>Parameter</code> return values.
   */
  private List<com.amazonaws.services.simplesystemsmanagement.model.Parameter> mockParameters() {
    List<com.amazonaws.services.simplesystemsmanagement.model.Parameter> params =
                 new ArrayList<com.amazonaws.services.simplesystemsmanagement.model.Parameter>();
    for(int i = 0; i < parameters.length; i++) {
      if(StringUtils.isEmpty(path) || parameters[i][NAME].startsWith(path)) {
        params.add(new com.amazonaws.services.simplesystemsmanagement.model.Parameter().withName(parameters[i][NAME]).withValue(parameters[i][VALUE]));
      }
    }
    return params;
  }

  /**
   * Mocks the static <code>getInstance()</code> and <code>getActiveInstance</code> methods
   * of the <code>Jenkins</code> class.
   */
  private void mockJenkins() {
    Jenkins jenkins = PowerMockito.mock(Jenkins.class);
    PowerMockito.mockStatic(Jenkins.class);
    PowerMockito.when(Jenkins.getInstance()).thenReturn(jenkins);
    PowerMockito.when(Jenkins.getActiveInstance()).thenReturn(jenkins);
  }

  /**
   * A mockito argument matcher that ensures the mocked getParameter() method
   * returns the correct mocked values.
   *
   * @author Rik Turnbull
   *
   */
  class GetParameterRequestMatcher extends ArgumentMatcher<GetParameterRequest> {
    private String name;

    /**
     * Creates a new {@link GetParameterRequestMatcher}.
     *
     * @param name   parameter name to match
     */
    public GetParameterRequestMatcher(String name) {
      this.name = name;
    }

    /**
     * Matches if <code>o</code> is a <code>GetParameterRequest</code> and its name
     * matches <code>name</code>.
     * @param o  a GetParameterRequest
     */
    public boolean matches(Object o) {
      if(o instanceof GetParameterRequest) {
        GetParameterRequest getParameterRequest = (GetParameterRequest)o;
        return getParameterRequest.getName().equals(name);
      } else {
        return false;
      }
    }
  }
}
