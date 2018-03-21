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

import com.amazonaws.regions.Region;
import com.amazonaws.regions.RegionUtils;

import com.cloudbees.jenkins.plugins.awscredentials.AWSCredentialsHelper;

import hudson.EnvVars;
import hudson.Extension;
import hudson.Launcher;
import hudson.Util;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.BuildListener;
import hudson.tasks.BuildWrapper;
import hudson.tasks.BuildWrapperDescriptor;
import hudson.util.ListBoxModel;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import org.kohsuke.stapler.DataBoundConstructor;

/**
 * A Jenkins {@link hudson.tasks.BuildWrapper} for AWS Parameter Store.
 *
 * @author Rik Turnbull
 *
 */
public class AwsParameterStoreBuildWrapper extends BuildWrapper {

  private static final Logger LOGGER = Logger.getLogger(AwsParameterStoreBuildWrapper.class.getName());

  private final String credentialsId;
  private final String regionName;
  private final String path;
  private final Boolean recursive;

  private transient AwsParameterStoreService parameterStoreService;

  /**
   * Creates a new {@link AwsParameterStoreBuildWrapper}.
   *
   * @param credentialsId   aws credentials id
   * @param regionName      aws region name
   * @param path            hierarchy for the parameter
   * @param recursive       fetch all parameters within a hierarchy
   */
  @DataBoundConstructor
  public AwsParameterStoreBuildWrapper(String credentialsId, String regionName, String path, Boolean recursive) {
    this.credentialsId = credentialsId;
    this.regionName = regionName;
    this.path = path;
    this.recursive = recursive;
  }

  /**
   * Gets AWS credentials identifier.
   * @return AWS credentials identifier
   */
  public String getCredentialsId() {
    return credentialsId;
  }

  /**
   * Gets AWS region name.
   * @return AWS region name
   */
  public String getRegionName() {
      return regionName;
  }

  /**
   * Gets path.
   * @return path
   */
  public String getPath() {
      return path;
  }

  /**
   * Gets recursive flag.
   * @return recursive
   */
  public Boolean getRecursive() {
     return recursive;
   }

  @Override
  public Environment setUp(AbstractBuild build, Launcher launcher, BuildListener listener) throws IOException, InterruptedException {
    return new AwsParameterStoreEnvironment(new AwsParameterStoreService(credentialsId, regionName), path, recursive);
  }

   /**
    * A Jenkins {@link hudson.model.Environment} implementation AWS Parameter Store.
    *
    * @author Rik Turnbull
    *
    */
  class AwsParameterStoreEnvironment extends Environment  {
    private AwsParameterStoreService awsParameterStoreService;
    private String path;
    private Boolean recursive;

    /**
     * Creates a new {@link AwsParameterStoreEnvironment}.
     *
     * @param awsParameterStoreService   AWS Parameter Store service
     * @param path                       hierarchy for the parameter
     * @param recursive                  fetch all parameters within a hierarchy
     */
    public AwsParameterStoreEnvironment(AwsParameterStoreService awsParameterStoreService, String path, Boolean recursive) {
        this.awsParameterStoreService = awsParameterStoreService;
        this.path = path;
        this.recursive = recursive;
    }

    @Override
    public void buildEnvVars(Map<String, String> env)  {
      awsParameterStoreService.buildEnvVars(env, path, recursive);
    }
  }

  /**
   * A Jenkins <code>BuildWrapperDescriptor</code> for the {@link AwsParameterStoreBuildWrapper}.
   *
   * @author Rik Turnbull
   *
   */
  @Extension
  public static final class DescriptorImpl extends BuildWrapperDescriptor  {
    @Override
    public String getDisplayName() {
      return Messages.displayName();
    }

    /**
     * Returns a list of AWS credentials identifiers.
     * @return {@link ListBoxModel} populated with AWS credential identifiers
     */
    public ListBoxModel doFillCredentialsIdItems() {
      return AWSCredentialsHelper.doFillCredentialsIdItems(Jenkins.getActiveInstance());
    }

    /**
     * Returns a list of AWS region names.
     * @return {@link ListBoxModel} populated with AWS region names
     */
    public ListBoxModel doFillRegionNameItems() {
      final ListBoxModel options = new ListBoxModel();
      final List<String> regionNames = new ArrayList<String>();
      final List<Region> regions = RegionUtils.getRegions();
      for(Region region : regions) {
        regionNames.add(region.getName());
      }
      Collections.sort(regionNames);
      options.add("- select -");
      for(String regionName : regionNames) {
        options.add(regionName);
      }
      return options;
    }

    @Override
    public boolean isApplicable(AbstractProject item) {
      return true;
    }
  }
}
