/*
 * Copyright (C) 2009 Jayway AB
 * Copyright (C) 2007-2008 JVending Masa
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.jayway.maven.plugins.android.phase09package;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.apache.commons.io.FileUtils;
import org.apache.commons.lang.StringUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.model.Resource;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.util.DefaultFileSet;

import com.jayway.maven.plugins.android.AbstractAndroidMojo;
import com.jayway.maven.plugins.android.CommandExecutor;
import com.jayway.maven.plugins.android.ExecutionException;
import static com.jayway.maven.plugins.android.common.AndroidExtension.APKLIB;


/**
 * Creates the apklib file.<br/>
 * apklib files do not generate deployable artifacts.
 *
 * @author nmaiorana@gmail.com
 * @goal apklib
 * @phase package
 * @requiresDependencyResolution compile
 */
public class ApklibMojo extends AbstractAndroidMojo {

    public void execute() throws MojoExecutionException, MojoFailureException {
        generateIntermediateAp_();

        CommandExecutor executor = CommandExecutor.Factory.createDefaultCommmandExecutor();
        executor.setLogger(this.getLog());

        File outputFile = createApkLibraryFile();

        // Set the generated .apklib file as the main artifact (because the pom states <packaging>apklib</packaging>)
        project.getArtifact().setFile(outputFile);
    }
    
    protected File createApkLibraryFile() throws MojoExecutionException {
        final File apklibrary = new File(project.getBuild().getDirectory(), project.getBuild().getFinalName() + "." + APKLIB);
        FileUtils.deleteQuietly(apklibrary);

        try {
            JarArchiver jarArchiver = new JarArchiver();
            jarArchiver.setDestFile(apklibrary);

            jarArchiver.addFile(androidManifestFile, "AndroidManifest.xml");
            addDirectory(jarArchiver, assetsDirectory, "assets");
            addDirectory(jarArchiver, resourceDirectory, "res");
            addDirectory(jarArchiver, sourceDirectory, "src");
            addJavaResources(jarArchiver, project.getBuild().getResources(), "src");

            jarArchiver.createArchive();
        } catch (ArchiverException e) {
            throw new MojoExecutionException("ArchiverException while creating ."+APKLIB+" file.", e);
        } catch (IOException e) {
            throw new MojoExecutionException("IOException while creating ."+APKLIB+" file.", e);
        }

        return apklibrary;
    }
    protected void addJavaResources(JarArchiver jarArchiver, List<Resource> javaResources, String prefix) throws ArchiverException {
        for (Resource javaResource : javaResources) {
            addJavaResource(jarArchiver, javaResource, prefix);
        }
    }

    /**
     * Adds a Java Resources directory (typically "src/main/resources") to a {@link JarArchiver}.
     *
     * @param jarArchiver
     * @param javaResource The Java resource to add.
     * @param prefix An optional prefix for where in the Jar file the directory's contents should go.
     * @throws ArchiverException
     */
    protected void addJavaResource(JarArchiver jarArchiver, Resource javaResource, String prefix) throws ArchiverException {
        if (javaResource != null) {
            final File javaResourceDirectory = new File(javaResource.getDirectory());
            if (javaResourceDirectory.exists()) {
                final DefaultFileSet javaResourceFileSet = new DefaultFileSet();
                javaResourceFileSet.setDirectory(javaResourceDirectory);
                javaResourceFileSet.setPrefix(endWithSlash(prefix));
                jarArchiver.addFileSet(javaResourceFileSet);
            }
        }
    }

    /**
     * Makes sure the string ends with "/"
     *
     * @param prefix any string, or null.
     * @return the prefix with a "/" at the end, never null.
     */
    protected String endWithSlash(String prefix) {
        prefix = StringUtils.defaultIfEmpty(prefix, "/");
        if (!prefix.endsWith("/")) {
            prefix = prefix + "/";
        }
        return prefix;
    }
    /**
     * Adds a directory to a {@link JarArchiver} with a directory prefix.
     *
     * @param jarArchiver
     * @param directory   The directory to add.
     * @param prefix      An optional prefix for where in the Jar file the directory's contents should go.
     * @throws ArchiverException
     */
    protected void addDirectory(JarArchiver jarArchiver, File directory, String prefix) throws ArchiverException {
        if (directory != null && directory.exists()) {
            final DefaultFileSet fileSet = new DefaultFileSet();
            fileSet.setPrefix(endWithSlash(prefix));
            fileSet.setDirectory(directory);
            jarArchiver.addFileSet(fileSet);
        }
    }

    /**
     * Generates an intermediate apk file (actually .ap_) containing the resources and assets.
     *
     * @throws MojoExecutionException
     */
    private void generateIntermediateAp_() throws MojoExecutionException {

        CommandExecutor executor = CommandExecutor.Factory.createDefaultCommmandExecutor();
        executor.setLogger(this.getLog());
        File[] overlayDirectories;

        if (resourceOverlayDirectories == null || resourceOverlayDirectories.length == 0) {
            overlayDirectories = new File[]{resourceOverlayDirectory};
        } else {
            overlayDirectories = resourceOverlayDirectories;
        }

        File androidJar = getAndroidSdk().getAndroidJar();
        File outputFile = new File(project.getBuild().getDirectory(), project.getBuild().getFinalName() + ".ap_");

        List<String> commands = new ArrayList<String>();
        commands.add("package");
        commands.add("-f");
        commands.add("-M");
        commands.add(androidManifestFile.getAbsolutePath());
        for (File resOverlayDir : overlayDirectories) {
            if (resOverlayDir != null && resOverlayDir.exists()) {
                commands.add("-S");
                commands.add(resOverlayDir.getAbsolutePath());
            }
        }
        if (combinedRes.exists()) {
            commands.add("-S");
            commands.add(combinedRes.getAbsolutePath());
        } else {
            if (resourceDirectory.exists()) {
                commands.add("-S");
                commands.add(resourceDirectory.getAbsolutePath());
            }
        }
        for (Artifact apkLibraryArtifact: getRelevantDependencyArtifacts()) {
        	if (apkLibraryArtifact.getType().equals(APKLIB)) {
        		commands.add("-S");
        		commands.add(getLibraryUnpackDirectory(apkLibraryArtifact)+"/res");
        	}
        }
		commands.add("--auto-add-overlay");
        if (assetsDirectory.exists()) {
            commands.add("-A");
            commands.add(assetsDirectory.getAbsolutePath());
        }
        if (extractedDependenciesAssets.exists()) {
            commands.add("-A");
            commands.add(extractedDependenciesAssets.getAbsolutePath());
        }
        commands.add("-I");
        commands.add(androidJar.getAbsolutePath());
        commands.add("-F");
        commands.add(outputFile.getAbsolutePath());
        if (StringUtils.isNotBlank(configurations)) {
            commands.add("-c");
            commands.add(configurations);
        }
        getLog().info(getAndroidSdk().getPathForTool("aapt") + " " + commands.toString());
        try {
            executor.executeCommand(getAndroidSdk().getPathForTool("aapt"), commands, project.getBasedir(), false);
        } catch (ExecutionException e) {
            throw new MojoExecutionException("", e);
        }
    }

}
