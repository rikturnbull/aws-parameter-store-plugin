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

import com.cloudbees.jenkins.plugins.awscredentials.AWSCredentialsHelper;

import java.util.Arrays;
import java.util.Collection;

import jenkins.model.Jenkins;

import hudson.model.AbstractBuild;
import hudson.util.ListBoxModel;

import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;
import org.junit.runners.Parameterized.Parameter;
import org.junit.runners.Parameterized.Parameters;
import org.mockito.Mockito;
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
@PrepareForTest({AwsParameterStoreBuildWrapper.DescriptorImpl.class, Jenkins.class, AWSCredentialsHelper.class})
public class AwsParameterStoreBuildWrapperDescriptorTest {

  /**
   * Setups mock service classes.
   */
  @Before
  public void setUp() {
    mockAWSCredentialsHelper();
    mockJenkins();
  }

  /**
   * Test constructor does not throw an Exception.
   */
  @Test
  public void testConstructor() {
    try {
      AwsParameterStoreBuildWrapper.DescriptorImpl descriptor = new AwsParameterStoreBuildWrapper.DescriptorImpl();
    } catch(Exception e) {
      Assert.fail("Unexpected exception: " + e.getMessage());
    }
  }

  /**
   * Test credential id drop-down is populated - well, is empty.
   */
  @Test
  public void testDoFillCredentialsIdItems() {
    AwsParameterStoreBuildWrapper.DescriptorImpl descriptor = new AwsParameterStoreBuildWrapper.DescriptorImpl();
    ListBoxModel credentials = descriptor.doFillCredentialsIdItems();
    Assert.assertEquals("credentials", null, credentials);
  }

  /**
   * Test region names drop-down is populated with region names.
   */
  @Test
  public void testDoFillRegionNameItems() {
    AwsParameterStoreBuildWrapper.DescriptorImpl descriptor = new AwsParameterStoreBuildWrapper.DescriptorImpl();
    ListBoxModel regionNames  = descriptor.doFillRegionNameItems();
    Assert.assertTrue("regionNames", regionNames.size() > 2);
  }

  /**
   * Test the display name is set correctly.
   */
  @Test
  public void testGetDisplayName() {
    AwsParameterStoreBuildWrapper.DescriptorImpl descriptor = new AwsParameterStoreBuildWrapper.DescriptorImpl();
    Assert.assertEquals("displayName", "Add AWS Parameter Store values to the environment", descriptor.getDisplayName());
  }

  /**
   * Test the applicable flag is set correctly.
   */
  @Test
  public void testIsApplicable() {
    AwsParameterStoreBuildWrapper.DescriptorImpl descriptor = new AwsParameterStoreBuildWrapper.DescriptorImpl();
    Assert.assertEquals("isApplicable", true, descriptor.isApplicable(null));
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
   * Mocks the static <code>getInstance()</code> and <code>getActiveInstance</code> methods
   * of the <code>Jenkins</code> class.
   */
  private void mockJenkins() {
    Jenkins jenkins = PowerMockito.mock(Jenkins.class);
    PowerMockito.mockStatic(Jenkins.class);
    PowerMockito.when(Jenkins.getInstance()).thenReturn(jenkins);
    PowerMockito.when(Jenkins.getActiveInstance()).thenReturn(jenkins);
  }
}
