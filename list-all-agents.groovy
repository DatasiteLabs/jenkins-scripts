import com.cloudbees.opscenter.server.model.*
import com.cloudbees.opscenter.server.clusterops.steps.*
import hudson.remoting.*
  
Jenkins.instance.getAllItems(ConnectedMaster.class)[0..2].each {
  println "agent: ${it.name} ${it.localEndpoint}"
}

println ""
