/**
 * This will generate a report of extraction jobs by user and space.
 */

// First, use extractions table to populate user/job duration lookup. Then iterate over lookup keys.
var lookup = new Object();

// Get type of a job message, if missing other info like previous messages
function determineJobType(jobMessage) {
    if (jobMessage == "SUBMITTED") {
        return "queue"
    } else if (jobMessage.indexOf("Started processing") > -1) {
        return "work"
    } else {
        return "work" // TODO: better option? Not all extractors send "Started processing"
    }
}

// To filter by time, include another clause in find eg: "start": {"$gte": ISODate("2020-01-01T00:00:00Z")}
db.extractions.find({"user_id":{$exists: 1}}).forEach(function(ext) {
    let jobid = ext.job_id;
    if (jobid == null) jobid = "";
    let userid = ext.user_id;
    let target = ext.file_id;
    let extract = ext.extractor_id;
    let jobtype = determineJobType(ext.status)
    // Not everything has Job id, so we consider resource + extractorname to be unique field.
    let uniquekey = target+" - "+extract;

    if (userid != null) {
        // Look up space and resource type - if it's in a folder, can get slow
        let spaceid = "";
        let resourceType = "file";
        let ds = db.datasets.findOne({"_id": target});
        if (ds != null) {
            spaceid = ds.spaces;
            resourceType = "dataset";
        } else {
            let ds = db.datasets.findOne({"files": target});
            if (ds != null) {
                spaceid = ds.spaces;
                resourceType = "file";
            } else {
                let fo = db.folders.findOne({"files": target});
                if (fo != null) {
                    let ds = db.datasets.findOne({"_id": fo.parentDatasetId});
                    if (ds != null) {
                        spaceid = ds.spaces;
                        resourceType = "file";
                    }
                }
            }
        }

        if (!(userid in lookup)) lookup[userid] = new Object();
        // Each resource x extractor has a separate queue of jobs
        if (!(uniquekey in lookup[userid])) lookup[userid][uniquekey] = {"jobs": []};
        let joblist = lookup[userid][uniquekey]["jobs"];
        if (!("current_job" in lookup[userid][uniquekey])) {
            lookup[userid][uniquekey]["current_job"] = {
                "target": target,
                "targetType": resourceType,
                "extract": extract,
                "space_id": spaceid,
                "job_id": jobid,
                "job_type": jobtype,
                "status_count": 1,
                "last_status": ext.status,
                "start": ext.start,
                "end": ext.start
            }
        } else {
            if (jobtype != lookup[userid][uniquekey]["current_job"]["job_type"]) {
                // if it's a new job, push current one to list
                joblist.push(lookup[userid][uniquekey]["current_job"]);
                lookup[userid][uniquekey]["jobs"] = joblist;
                lookup[userid][uniquekey]["current_job"] = {
                    "target": target,
                    "targetType": resourceType,
                    "extract": extract,
                    "space_id": spaceid,
                    "job_id": jobid,
                    "job_type": jobtype,
                    "status_count": 1,
                    "last_status": ext.status,
                    "start": ext.start,
                    "end": ext.start
                };
            } else {
                // Otherwise, it's another part of ongoing job, just update times
                // Records are chronological by ID so skip start comparison
                let end = lookup[userid][uniquekey]["current_job"]["end"];
                if (end < ext.start) lookup[userid][uniquekey]["current_job"]["end"] = ext.start;
                // If we have a DONE message already, avoid Python millisecond truncation issues by ignoring later ones
                let status = lookup[userid][uniquekey]["current_job"]["last_status"];
                if (status != "DONE") lookup[userid][uniquekey]["current_job"]["last_status"] = ext.status;
                lookup[userid][uniquekey]["current_job"]["status_count"] += 1;
            }
        }
    }
});

function clean(str) {
    // Strip ObjectId(" ") wrapping
    try {
        return str.substr(10, 24);
    }
    catch (error) {
        return String(str).substr(10, 24);
    }
}

print("\""+["userid", "username", "email", "resource_id", "resource_type", "space_id", "extractor",
    "job_id", "job_type", "status_count", "last_status", "start", "end", "duration_ms"].join("\",\"")+"\"")
for (userid in lookup) {
    // get user info
    let username = "";
    let email = "";
    let user = db.social.users.findOne({"_id": ObjectId(clean(userid))});
    if (user != null) {
        username = user.fullName;
        email = user.email;
    }

    for (uniquekey in lookup[userid]) {
        let resource = uniquekey.split(" - ")[0];
        let extractor = uniquekey.split(" - ")[1];

        let joblist = lookup[userid][uniquekey]["jobs"];
        for (var i=0; i<joblist.length; i++) {
            let jobid = joblist[i]["job_id"];
            let restype = joblist[i]["targetType"];
            let spids = joblist[i]["space_id"]; //(joblist[i]["space_id"]).map(spid => clean(spid)).join(",");
            let start = joblist[i]["start"];
            let end = joblist[i]["end"];
            let type = joblist[i]["job_type"];
            let count = joblist[i]["status_count"];
            let status = joblist[i]["last_status"];
            let duration = (end - start);
            if (duration > 0)
                print("\""+[clean(userid), username, email, resource, restype, clean(spids), extractor,
                    clean(jobid), type, count, status, start, end, duration].join("\",\"")+"\"")
        };

        // Get final current_job as well
        let job = lookup[userid][uniquekey]["current_job"];
        let jobid = job["job_id"];
        let restype = job["targetType"];
        let spids = job["space_id"]; //(job["space_id"]).map(spid => clean(spid)).join(",");
        let start = job["start"];
        let end = job["end"];
        let type = job["job_type"];
        let count = job["status_count"];
        let status = job["last_status"];
        let duration = (end - start);
        if (duration > 0)
            print("\""+[clean(userid), username, email, resource, restype, clean(spids), extractor,
                clean(jobid), type, count, status, start, end, duration].join("\",\"")+"\"")
    }
}