/*
* This script will execute all jobs matching a specific Regex pattern
* defined in the variable pattern at the top of the script. Change the
* pattern to be able to bulk execute Develop jobs from the script console
*/
def pattern = /Dev-and-PR-Pipelines/

Jenkins.instance.getAllItems(AbstractItem.class).each {
    if (assert it.fullName =~ pattern) {
        it.scheduleBuild()
    }
};