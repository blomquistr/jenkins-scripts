// taken from https://support.cloudbees.com/hc/en-us/articles/216241937-Migration-Guide-CloudBees-Jenkins-Platform-and-CloudBees-Jenkins-Team-
// provided by Cloudbees 

Jenkins.instance.getAllItems().each { j ->
  if (j instanceof com.cloudbees.hudson.plugins.folder.Folder) { return }
  j.disable()
}