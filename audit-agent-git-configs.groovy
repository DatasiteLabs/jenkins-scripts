import com.cloudbees.hudson.plugins.folder.Folder
import com.cloudbees.opscenter.bluesteel.folder.BlueSteelTeamFolder
import com.infradna.hudson.plugins.backup.BackupProject
import groovy.json.JsonOutput
import hudson.model.FreeStyleProject
import hudson.model.Item
import hudson.plugins.git.UserRemoteConfig
import jenkins.model.GlobalConfiguration
import jenkins.model.Jenkins
import jenkins.plugins.git.AbstractGitSCMSource
import jenkins.scm.api.SCMSource
import org.jenkinsci.plugins.github.config.GitHubPluginConfig
import org.jenkinsci.plugins.github.config.GitHubServerConfig
import org.jenkinsci.plugins.github_branch_source.GitHubConfiguration
import org.jenkinsci.plugins.workflow.job.WorkflowJob
import org.jenkinsci.plugins.workflow.libs.GlobalLibraries
import org.jenkinsci.plugins.workflow.libs.LibraryCachingConfiguration
import org.jenkinsci.plugins.workflow.libs.LibraryConfiguration
import org.jenkinsci.plugins.workflow.libs.SCMSourceRetriever
import org.jenkinsci.plugins.workflow.multibranch.WorkflowMultiBranchProject

interface Library {
    String name
    String repository
    String branch
}

class DSGitHubServerConfig {
    String name
    String apiUrl
    String credentialsId

    DSGitHubServerConfig(GitHubServerConfig ghServerConfig) {
        this.name = ghServerConfig.name
        this.apiUrl = ghServerConfig.apiUrl
        this.credentialsId = ghServerConfig.credentialsId
    }
}

class DSScmSource {
    String credentialsId
    String remote
    String id
    String name

    DSScmSource(AbstractGitSCMSource scm) {
        this.credentialsId = scm.getCredentialsId()
        this.remote = scm.getRemote()
        this.id = scm.getId()
    }

    DSScmSource(UserRemoteConfig scm) {
        this.credentialsId = scm.getCredentialsId()
        this.remote = scm.getUrl()
        this.name = scm.getName()
    }
}

class DSJob {
    String name
    String url
    List<DSScmSource> sources = []
    String remoteScriptRepo
    String inlineScript
    String remoteScriptPath
    List<String> scriptSteps = []

    DSJob(String baseUrl, Item jobItem) {
        this.name = jobItem.getName()
        this.url = "${baseUrl}${jobItem.getUrl()}"
        if (jobItem.hasProperty('sources')) {
            jobItem.sources.each {
                this.sources << new DSScmSource(it.source)
            }
        }

        if (jobItem.hasProperty('SCMs')) {
            jobItem.SCMs.each { scm ->
                scm.getUserRemoteConfigs().each { config ->
                    this.sources << new DSScmSource(config)
                }

            }
        }

        if (jobItem.respondsTo('getBuilders')) {
            jobItem.getBuilders().each { buildStep ->
                if (buildStep.respondsTo('getCommand')) {
                    String command = buildStep.getCommand()
                    this.scriptSteps << command.bytes.encodeBase64().toString()
                }
            }
        }

        if (jobItem.respondsTo('getDefinition')) {
            def jobDef = jobItem.getDefinition()
            if (jobDef.hasProperty('script')) {
                this.inlineScript = jobDef.script.bytes.encodeBase64().toString()
            }

            if (jobDef.hasProperty('scriptPath')) {
                def remoteConfigs = jobDef.scm.getUserRemoteConfigs()
                assert remoteConfigs.size() == 1
                this.remoteScriptPath = jobDef.scriptPath
                this.remoteScriptRepo = remoteConfigs[0].getUrl()
            }
        }
    }
}

class DSLibraryCachingConfiguration {
    String refreshTimeMinutes = ""
    String excludedVersionsStr = ""

    DSLibraryCachingConfiguration(LibraryCachingConfiguration cachingConfiguration) {
        this.refreshTimeMinutes = cachingConfiguration?.refreshTimeMinutes
        this.excludedVersionsStr = cachingConfiguration?.excludedVersionsStr
    }

}


class DSGlobalLibraryConfig {
    String name
    String defaultVersion
    Boolean implicit
    Boolean allowVersionOverride
    Boolean includeInChangesets
    DSLibraryCachingConfiguration cachingConfiguration
    DSScmSource scm

    DSGlobalLibraryConfig(LibraryConfiguration libraryConfig) {
        this.name = libraryConfig.getName()
        this.defaultVersion = libraryConfig.getDefaultVersion()
        this.implicit = libraryConfig.isImplicit()
        this.allowVersionOverride = libraryConfig.isAllowVersionOverride()
        this.includeInChangesets = libraryConfig.getIncludeInChangesets()
        this.cachingConfiguration = new DSLibraryCachingConfiguration(libraryConfig.cachingConfiguration)
        this.scm = new DSScmSource(libraryConfig.retriever.scm)
    }
}

//@ToString(includeNames=true)
class AgentReportItem {
    String url
    String name
    Map<String, DSGlobalLibraryConfig> libraries = [:]
    List<DSGitHubServerConfig> gitHubServerConfigs = []
    Map<String, DSJob> jobs = [:]
    List<String> emptyJobs = []

    AgentReportItem(Jenkins jenkinsInstance, String name) {
        this.url = jenkinsInstance.getRootUrl()
        this.name = name
    }

//    @Override
//    String toString() {
//        return " ${this.url}"
//    }
}

Map<String, AgentReportItem> agentReport = new HashMap()
def jenkins = Jenkins.getInstance()
def name = jenkins.getComputer("").getHostName().split('\\.')[1]
agentReport[name] = new AgentReportItem(jenkins, name)

println "Jenkins url: ${jenkins.getRootUrl()}"

GitHubConfiguration gitHubConfig = GlobalConfiguration.all().get(GitHubConfiguration.class)
def endpoints = gitHubConfig.getEndpoints()

endpoints.each { it.dump() }

println "Found github branch source endpoints: ${endpoints.size()}"
assert endpoints.size() == 0

def githubConfig = jenkins.getDescriptorByType(GitHubPluginConfig.class)
def serverConfigs = githubConfig.getConfigs().findAll { it.apiUrl.contains('github.com') }

serverConfigs.each { serverConfig ->
    agentReport[name].gitHubServerConfigs << new DSGitHubServerConfig(serverConfig)
//    println("Found github config '${serverConfig.getName()}' at root: " + serverConfig.getApiUrl())
}

println "\nShared Libraries"
// Get the list of configured global libraries
def globalLibrariesConfig = jenkins.getDescriptor(GlobalLibraries)
def globalLibraries = globalLibrariesConfig.getLibraries()

// Print the details of each global library
globalLibraries.each { lib ->
    agentReport[name].libraries[lib.name] = new DSGlobalLibraryConfig(lib)
    if (lib.retriever instanceof SCMSourceRetriever) {
        SCMSource scmSource = lib.retriever.scm
        println "Library: ${lib.name} ${scmSource.remote}"
    } else {
        lib.dump()
        throw new Exception("Found unknown library type")
    }
}

println ""

jenkins.model.Jenkins.get().getAllItems().each { job ->
    def parent = job.getParent()
    def jobUrl = "JOB: ${jenkins.getRootUrl()}${job.url}"
    def jobKey = job.url
    if (agentReport[name].jobs.containsKey(jobKey)) {
        throw new Exception("Key already exists for ${jobKey}")
    }
    if (job instanceof WorkflowMultiBranchProject) {
        agentReport[name].jobs[jobKey] = new DSJob(jenkins.getRootUrl(), job)
    } else {
//        exclude child multibranch jobs
        if (!parent.getClass().name.contains('WorkflowMultiBranchProject')) {
            switch (job) {
                case { it instanceof WorkflowJob }:
                    agentReport[name].jobs[jobKey] = new DSJob(jenkins.getRootUrl(), job)
                    def jobDef = job.getDefinition()
                    if (!jobDef.hasProperty('script') && !jobDef.hasProperty('scm')) {
                        agentReport[name].emptyJobs << jobKey
                        println "[EMPTY] No script or scm found, job may be empty. ${jobUrl}"
                    }
                    break
                case { it instanceof FreeStyleProject }:
                    agentReport[name].jobs[jobKey] = new DSJob(jenkins.getRootUrl(), job)
                    def buildSteps = job.getBuilders()
                    if (buildSteps.size() < 1) {
                        agentReport[name].emptyJobs << jobKey
                        println "[EMPTY] No builders found, job may be empty ${jobUrl}"
                    }
                    break
                case Folder:
                    break
                case BlueSteelTeamFolder:
                    break
                case BackupProject:
                    break
                default:
                    println "SKIP type: ${job.getClass().name} ${jobUrl}"
                    break
            }
        }
    }
}

println ""

//println agentReport

println ""

return JsonOutput.toJson(agentReport)
