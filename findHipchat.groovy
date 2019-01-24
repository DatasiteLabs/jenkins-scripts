Jenkins.instance.getAllItems(AbstractItem.class).each { job ->
	def jobName = job.getFullName()
  try {
		def hipchat = job.publishersList.find { it.getClass().toString().contains('HipChatNotifier') }
      if (hipchat) {
        def room = hipchat.getRoom()
        println("${jobName}: ${room}\n")
      }
  } catch(all) {
    // noop
  }
}