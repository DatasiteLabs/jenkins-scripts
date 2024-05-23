import jenkins.model.*;
import org.jenkinsci.plugins.github_branch_source.*;
import java.util.*;
import org.jenkinsci.plugins.github.config.GitHubPluginConfig
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries
import jenkins.plugins.git.GitSCMSource
import jenkins.scm.api.SCMSource
import hudson.plugins.git.GitSCM
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject

def jenkins = Jenkins.getInstance()

println "Jenkins url: ${jenkins.getRootUrl()}"

GitHubConfiguration gitHubConfig = GlobalConfiguration.all().get(GitHubConfiguration.class)
def endpoints = gitHubConfig.getEndpoints()

endpoints.each { it.dump() }

println "Found github branch source endpoints: ${endpoints.size()}"

def githubConfig = jenkins.getDescriptorByType(GitHubPluginConfig.class)
def serverConfigs = githubConfig.getConfigs()

serverConfigs.each { serverConfig ->
  println("Found github config '${serverConfig.getName()}' at root: "+serverConfig.getApiUrl())
}

def globalLibrariesConfig = jenkins.getDescriptor(org.jenkinsci.plugins.workflow.libs.GlobalLibraries)

println "\nShared Libraries"
// Get the list of configured global libraries
def globalLibraries = globalLibrariesConfig.getLibraries()

// Print the details of each global library
globalLibraries.each { lib ->    
    if (lib.retriever instanceof org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever) {
        SCMSource scmSource = lib.retriever.scm
      println "Library: ${lib.name} ${scmSource.repositoryUrl}"
    }
}

println "\nJobs"

jenkins.model.Jenkins.get().getAllItems().each { job ->
  def parent = job.getParent()
  def jobUrl = "JOB: ${jenkins.getRootUrl()}${job.url}"
  if (!parent.getClass().name.contains('WorkflowMultiBranchProject')) {
    if (job.getClass().name.contains('WorkflowJob')) {
      println jobUrl
      job.SCMs.each { scm -> 
        scm.getRepositories().each { repo -> 
          repo.uris.each { uri -> 
	          println "\trepo: ${uri.path}"
          }      
        }
        
      }
//	  println "${job} -> ${parent}"
      def script = job.getDefinition().script
      if (script.contains('github.com')) {
        println "----- Pipeline Definition -----"
        println script
        println "-------------------------------"
      }
    }
  }
  if (job instanceof WorkflowMultiBranchProject) {
    job.sources.each { it -> 
      println jobUrl
      println "\trepo: ${it.source.repositoryUrl}"
    }
  }
}

println ""
