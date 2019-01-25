import jenkins.model.Jenkins
import hudson.plugins.parameterizedtrigger.*

hipchatSlackMap = [:]
hipchatSlackMap.put('default', 'UNKOWN')
hipchatSlackMap.put('DevOps', 'DEVOPS_ALERT')
hipchatSlackMap.put('QA ONLY', 'QA')
hipchatSlackMap.put('DataQualityAlerts', 'ANALYTICS_ALERT')
hipchatSlackMap.put('J-Trial Room', 'UNKOWN')

hipchatJobMap = [:]
hipchatJobMap.put('default', 'FAILED')
hipchatJobMap.put('FAILURE', 'FAILED')
hipchatJobMap.put('SUCCESS', 'UNSTABLE_OR_BETTER')

slackTypeMap = [:]
slackTypeMap.put('default', 'FAILURE')
slackTypeMap.put('FAILURE', 'FAILURE')
slackTypeMap.put('SUCCESS', 'SUCCESS')

def buildSlackTrigger(def hipchatType, def message, def room = 'UNKOWN') {
    def triggerWithNoParameters = false
    def triggerFromChildProjects = false
    def resultCondition = hipchatJobMap.get(hipchatType.toString()) ?: 'FAILED'
    def type = slackTypeMap.get(hipchatType.toString()) ?: 'FAILURE'
    def projects = 'Javelin/Pipelines-and-Trains/slack-send'
    def properties = "slackMessageType=$type \nslackChannel=$room \nslackMessage=$message"
    def configParameters = new PredefinedBuildParameters(properties)
    def triggerConfig = new BuildTriggerConfig(projects, ResultCondition[resultCondition], triggerWithNoParameters, [configParameters], triggerFromChildProjects)
    new BuildTrigger([triggerConfig])
}

static String cleanMessage(String msg) {
    def msgFixed = msg.replaceAll('<br>', '\n')
            .replaceAll('<b>', '')
            .replaceAll('</b>', '')
            .replaceAll(/<a.*href=.(.*).>(.*)<\/a>/, /<$1 | $2>/)
            .replaceAll('\\$URL', '\\$BUILD_URL')
            //.replaceAll('\\$', '\\\\\\$')
    // println "$msg\n$msgFixed\n"
    return msgFixed

}

void updateJob(def job, def message, def hipchatType, def room = 'UNKOWN') {
    def parseMsg = cleanMessage(message)
    println "=> $room\n$hipchatType\n"

    def slackFailedBuildTrigger = buildSlackTrigger(hipchatType, parseMsg, room)
    job.publishersList.removeAll { it.getClass().toString().contains('HipChatNotifier') }
    job.publishersList.add(slackFailedBuildTrigger)

    job.save()
    println "updated."
    return
}

Jenkins.instance.getAllItems(AbstractItem.class).each { job ->
    def jobName = job.getFullName()
    if (job.hasProperty('publishersList')) {
        def hipchat = job.publishersList.find { it.getClass().toString().contains('HipChatNotifier') }
        if (hipchat) {
            String lookupRoom = (hipchat.getRoom()) ? hipchat.getRoom() : 'UNKOWN'
            String room = hipchatSlackMap.get(lookupRoom)
//        println hipchat.metaClass.methods*.name.sort().unique()
            println("${jobName}: ${room} : ${hipchat.getNotifications().size()}\n")
            def notifications = hipchat.getNotifications()
            notifications.each { it ->
                updateJob(job, it.messageTemplate, it.notificationType, room)
//          println it.dump() + "\n"

            }
            println("${jobName}: ${room} END\n\n")
            //println hipchat.metaClass.methods*.name.sort().unique()
        } else {
            // println "skiping.... ${jobName}"
        }
    }
}
