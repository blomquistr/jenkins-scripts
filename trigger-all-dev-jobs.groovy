/*
* This script will execute all jobs matching a specific Regex pattern
* defined in the variable pattern at the top of the script. Change the
* pattern to be able to bulk execute Develop jobs from the script console
*/
def pattern = /\/Dev-and-PR-Pipelines\/.*\/master/

Jenkins.instance.getAllItems(AbstractItem.class).each {
    def matches = it.fullName =~ pattern
    if (matches.size() > 0) {
        println("Scheduling build for ${it.fullName}")
        it.scheduleBuild()
    }
};