"""Offline unit tests for corpus categorization, splitting and gzip readers."""
import gzip,json
from bozer_neural.data import is_dialogue_path,read_records
from bozer_neural.prepare_100k_corpus import CATEGORIES,category,split_records
from bozer_neural.validate_corpus import validate_splits

def rec(i,cat):
    return {"category":cat,"user":"Question unique numéro %s : explique ce sujet en détail."%i,
    "assistant":"Réponse structurée et unique numéro %s, avec les éléments essentiels."%i,
    "language":"fr","source":"fixture","source_license":"test","source_id":str(i),"source_group_id":"fixture:"+str(i)}

def write(path,rows):
    with gzip.open(path,"wt",encoding="utf-8") as f:
        for row in rows:f.write(json.dumps(row,ensure_ascii=False)+"\n")

def test_classifier_categories():
    assert category("How do I keep my password secure?","Use a password manager.")=="securite_confidentialite_honnetete"
    assert category("Write a Python function","Here is the code.")=="utilisation_outils_verification"
    assert category("Plan a weekly project schedule","Start with milestones.")=="planification_missions"
    assert category("Solve this equation","The answer is four.")=="raisonnement_resolution"
    assert category("What is photosynthesis?","It is a biological process.")=="comprehension_demande"

def test_gzip_and_sharded_dialogue_reading(tmp_path):
    cats=list(CATEGORIES)
    dirs=[tmp_path/n for n in ("train","validation","test")]
    for d in dirs:d.mkdir()
    rows=[rec(i,cats[i%6]) for i in range(120)]
    val=[rec(i+1000,cats[i%6]) for i in range(30)]
    test=[rec(i+2000,cats[i%6]) for i in range(30)]
    write(dirs[0]/"part-00000.jsonl.gz",rows)
    write(dirs[1]/"part-00000.jsonl.gz",val)
    write(dirs[2]/"part-00000.jsonl.gz",test)
    assert is_dialogue_path(dirs[0])
    assert len(read_records(dirs[0]))==120
    report=validate_splits(*dirs)
    assert sum(report["train"].values())==120
    assert sum(report["validation"].values())==30
    assert sum(report["test"].values())==30

def test_stratified_split_sizes_and_group_separation():
    rows=[rec(i,CATEGORIES[i%6]) for i in range(600)]
    parts=split_records(rows,540,30,30)
    assert [len(parts[n]) for n in ("train","validation","test")]==[540,30,30]
    groups=[{x["source_group_id"] for x in parts[n]} for n in ("train","validation","test")]
    assert not(groups[0]&groups[1] or groups[0]&groups[2] or groups[1]&groups[2])
    for part in parts.values():assert {x["category"] for x in part}==set(CATEGORIES)
