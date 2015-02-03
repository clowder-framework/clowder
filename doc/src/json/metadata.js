/**
 * Example of new metadata format.
 *
 * Created by lmarini on 2/2/15.
 */
[
    {
        "@context": [
            "http://medici.ncsa.illinois.edu/metadata.jsonld",
            "http://medici.ncsa.illinois.edu/prov.jsonld",
            {
                "extractor_id": {
                    "@id": "http://dts.ncsa.illinois.edu/api/extractor/id",
                    "@type": "@id"
                },
                "score": "http://www.vision.caltech.edu/Image_Datasets/Caltech101/score",
                "category": "http://www.vision.caltech.edu/Image_Datasets/Caltech101/category"
            }],
        "created_at": "Fri Jan 16 15:57:20 CST 2015", // change to Fri Jan 16 15:57:20 -0700 2015 ?
        "agent": {
            "agent_type": "cat:extractor",
            "extractor_id": "http://dts.ncsa.illinois.edu/api/extractors/ncsa.cv.caltech101"
        },
        "content": {
            "score": [
                "-0.275160"
            ],
            "category": [
                "wrench"
            ]
        }
    },
    {
        "@context": [
            "http://medici.ncsa.illinois.edu/metadata.jsonld",
            "http://medici.ncsa.illinois.edu/prov.jsonld",
            {
                "abstract": "http://purl.org/dc/terms/abstract",
                "alternative": "http://purl.org/dc/terms/alternative"
            }],
        "created_at": "Fri Jan 16 9:51:20 CST 2015",
        "agent": {
            "@type": "cat:user",
            "user_id": "http://dts.ncsa.illinois.edu/api/users/52f1749ad6c40e37d0fe2ee7"
        },
        "content": {
            "abstract": [
                "This is the abstract"
            ],
            "alternative": [
                "This is the alternate title"
            ]
        }
    },
]