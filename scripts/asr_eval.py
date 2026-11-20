#!/usr/bin/env python3
"""
ASR Pronunciation Evaluator for Lector TTS Benchmarks.
Uses local whisper-cli (whisper.cpp) with ggml-base.en.bin to transcribe audio samples
and checks for the 19 required theological and biblical terms.
"""

import argparse
import glob
import json
import os
import re
import subprocess
import sys

HARD_TERMS = {
    "Melchizedek": [r"\bmelchizedek\b", r"\bmelchisedec\b", r"\bmelchisedek\b"],
    "Nebuchadnezzar": [r"\bnebuchadnezzar\b", r"\bnebuchadrezzar\b"],
    "Habakkuk": [r"\bhabakkuk\b", r"\bhabacuc\b"],
    "Zerubbabel": [r"\bzerubbabel\b", r"\bzorobabel\b"],
    "Theotokos": [r"\btheotokos\b", r"\btheotocos\b"],
    "hypostatic union": [r"\bhypostatic\s+(?:union)?\b", r"\bhypostatic\b"],
    "consubstantial": [r"\bconsubstantial\b"],
    "Filioque": [r"\bfilioque\b", r"\bfilio\s*quay\b"],
    "anamnesis": [r"\banamnesis\b"],
    "ex opere operato": [r"\b(?:ex\s+)?opere\s+operato\b", r"\bex\s*oper\b"],
    "Tertullian": [r"\btertullian\b"],
    "Irenaeus": [r"\birenaeus\b", r"\biren\w+\b"],
    "Ignatius of Antioch": [r"\bignatius(?:\s+of\s+antioch)?\b"],
    "Chrysostom": [r"\bchrysostom\b"],
    "Septuagint": [r"\bseptuagint\b"],
    "Magisterium": [r"\bmagisterium\b"],
    "1 Cor 15:3-8": [
        r"\b(?:1|first)\s+corinthians?\s+(?:chapter\s+)?15(?::|\s+verses?\s+)3(?:\s*-\s*|(?:\s+to\s+))8\b",
        r"\b(?:1|first)\s+corinthians\b"
    ],
    "CCC 1996": [
        r"\b(?:ccc|catechism(?:\s+of\s+the\s+catholic\s+church)?)\s+(?:paragraph\s+)?1996\b",
        r"\bnineteen\s+ninety[- ]six\b",
        r"\b1996\b"
    ],
    "Jn 6:53": [
        r"\bjohn\s+(?:chapter\s+)?(?:6|six)(?:,|:|\s*,?\s*verse)\s*(?:53|fifty[- ]three)\b",
        r"\bjn\s+6:53\b"
    ]
}

def transcribe_audio(wav_path: str, whisper_bin: str, model_path: str) -> str:
    cmd = [whisper_bin, "-m", model_path, "-f", wav_path, "-nt", "-np"]
    res = subprocess.run(cmd, capture_output=True, text=True)
    if res.returncode != 0:
        print(f"Error running transcription on {wav_path}: {res.stderr}", file=sys.stderr)
        return ""
    return res.stdout.strip()

def evaluate_transcript(text: str) -> dict:
    lower_text = text.lower()
    hits = {}
    for term, patterns in HARD_TERMS.items():
        matched = False
        for pat in patterns:
            if re.search(pat, lower_text, re.IGNORECASE):
                matched = True
                break
        hits[term] = matched
    return hits

def main():
    parser = argparse.ArgumentParser(description="Evaluate TTS pronunciations via local whisper.cpp ASR")
    parser.add_argument("--wav-dir", required=True, help="Directory containing sample .wav files")
    parser.add_argument("--whisper-bin", default="/opt/homebrew/bin/whisper-cli")
    parser.add_argument("--model-path", default="/Users/myhome/.cache/whisper-cpp/models/ggml-base.en.bin")
    parser.add_argument("--output-json", default=None, help="Path to save evaluation JSON results")
    args = parser.parse_args()

    wavs = sorted(glob.glob(os.path.join(args.wav_dir, "*.wav")))
    if not wavs:
        print(f"No .wav files found in {args.wav_dir}")
        sys.exit(1)

    print(f"Found {len(wavs)} wav files in {args.wav_dir}")
    all_results = {}

    for wav in wavs:
        name = os.path.basename(wav)
        print(f"\nTranscribing {name}...")
        transcript = transcribe_audio(wav, args.whisper_bin, args.model_path)
        hits = evaluate_transcript(transcript)
        score = sum(1 for v in hits.values() if v)
        print(f"  Score: {score}/{len(HARD_TERMS)} hard words recognized.")
        for term, hit in hits.items():
            status = "HIT" if hit else "MISS"
            print(f"    [{status}] {term}")
        all_results[name] = {
            "file": name,
            "transcript": transcript,
            "hits": hits,
            "score": score,
            "total": len(HARD_TERMS)
        }

    if args.output_json:
        with open(args.output_json, "w") as f:
            json.dump(all_results, f, indent=2)
        print(f"\nSaved evaluation results to {args.output_json}")

if __name__ == "__main__":
    main()
