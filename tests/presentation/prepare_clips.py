#!/usr/bin/env python3
import argparse
from datetime import datetime
import json
from pathlib import Path
import re
import subprocess
import xml.etree.ElementTree as ET
import imageio_ffmpeg

SELECTIONS = [
    ("lab1", "F03_Node_Connections_And_Interval", "F03_interval_applied", "lab1-node-settings", 3),
    ("lab2", "F02_Network_And_Device_Path", "F02_opnsense_api", "lab2-opnsense-api", 4),
    ("lab3", "F04_Authenticated_Web_Terminal", "F04_terminal", "lab3-reverse-terminal", 5),
    ("lab4", "F06_Mock_AI_Review_Execution_And_Rejection", "F06_ai_executed", "lab4-ovs-ai-query", 6),
]


def status_time(element):
    return datetime.fromisoformat(element.find("status").get("start"))


def make_clips(source_root, output, labs=None):
    encoder = imageio_ffmpeg.get_ffmpeg_exe()
    output.mkdir(parents=True, exist_ok=True)
    manifest_path = output / "manifest.json"
    existing = json.loads(manifest_path.read_text()) if manifest_path.exists() else []
    manifest = {v["lab"]:v for v in existing}
    for lab, test_name, capture, name, slide in SELECTIONS:
        if labs and lab not in labs:
            continue
        result = ET.parse(source_root / "robot" / lab / "output.xml").getroot()
        test = next(value for value in result.iter("test") if value.get("name") == test_name)
        context = next(value for value in test.iter("kw") if value.get("name") == "New Context")
        capture_kw = next(value for value in test.iter("kw") if value.get("name") == "Take Screenshot"
            and any(capture in (argument.text or "") for argument in value.findall("arg")))
        moment = (status_time(capture_kw) - status_time(context)).total_seconds()
        start = max(0, moment - 14)
        videos = list((source_root / "robot" / lab / "videos" / lab / test_name).glob("*.webm"))
        if len(videos) != 1:
            raise RuntimeError("Expected exactly one final recording for " + test_name)
        clip_length = min(22, moment - start - 0.5) if lab == "lab1" else 22
        video = output / (name + ".mp4")
        poster = output / (name + ".png")
        subprocess.run([encoder, "-hide_banner", "-loglevel", "error", "-y",
            "-ss", str(start), "-i", str(videos[0]), "-t", str(clip_length), "-an",
            "-vf", "scale=1280:800", "-c:v", "libx264", "-pix_fmt", "yuv420p",
            "-preset", "fast", "-crf", "21", "-threads", "2", "-movflags", "+faststart", str(video)], check=True)
        probe = subprocess.run([encoder, "-hide_banner", "-i", str(video)],
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, text=True)
        duration = re.search(r"Duration: (\d+):(\d+):([\d.]+)", probe.stderr)
        seconds = int(duration[1]) * 3600 + int(duration[2]) * 60 + float(duration[3])
        poster_time = min(max(0, moment - start), max(0, seconds - 0.2))
        subprocess.run([encoder, "-hide_banner", "-loglevel", "error", "-y",
            "-ss", str(poster_time), "-i", str(video), "-frames:v", "1", str(poster)], check=True)
        subprocess.run([encoder, "-hide_banner", "-loglevel", "error", "-i", str(video),
            "-f", "null", "-"], check=True)
        manifest[lab] = {"lab": lab, "test": test_name, "slide_number": slide,
            "source_recording": str(videos[0].relative_to(source_root)),
            "clip_start_seconds": round(start, 3), "duration_seconds": seconds,
            "editing": "Original speed; Lab1 ends before full-page capture resizes the recording viewport.",
            "video": str(video.resolve()), "poster": str(poster.resolve()),
            "video_codec": "H.264", "pixel_format": "yuv420p", "decode_verified": True}
    values = [manifest[lab] for lab, *_ in SELECTIONS if lab in manifest]
    manifest_path.write_text(json.dumps(values, ensure_ascii=False, indent=2))
    return values



def make_deployment_clip(source_root, output):
    encoder = imageio_ffmpeg.get_ffmpeg_exe()
    result = ET.parse(source_root / "robot/lab1/output.xml").getroot()
    test = next(t for t in result.iter("test") if t.get("name") == "D01_Frontend_Download_And_Actual_SSH_Deployment")
    context = next(k for k in test.iter("kw") if k.get("name") == "New Context")
    download = next(k for k in test.iter("kw") if k.get("name") == "Show And Click" and any("Agent 다운로드 (tar.gz)" in (a.text or "") for a in k.findall("arg")))
    capture = next(k for k in test.iter("kw") if k.get("name") == "Take Screenshot" and any("_ssh" in (a.text or "") for a in k.findall("arg")))
    start = max(0, (status_time(download)-status_time(context)).total_seconds()-2)
    end = (status_time(capture)-status_time(context)).total_seconds()-0.25
    recording = list((source_root/"robot/lab1/videos/lab1"/test.get("name")).glob("*.webm"))
    if len(recording) != 1: raise RuntimeError("Expected one final D01 video")
    output.mkdir(parents=True,exist_ok=True)
    video,poster = output/"lab1-frontend-ssh.mp4",output/"lab1-frontend-ssh.png"
    subprocess.run([encoder,"-hide_banner","-loglevel","error","-y","-ss",str(start),"-i",str(recording[0]),"-t",str(end-start),"-an","-vf","scale=1280:800","-c:v","libx264","-pix_fmt","yuv420p","-preset","fast","-crf","21","-threads","2","-movflags","+faststart",str(video)],check=True)
    subprocess.run([encoder,"-hide_banner","-loglevel","error","-y","-ss",str(end-start-0.3),"-i",str(video),"-frames:v","1",str(poster)],check=True)
    subprocess.run([encoder,"-hide_banner","-loglevel","error","-i",str(video),"-f","null","-"],check=True)
    duration=imageio_ffmpeg.count_frames_and_secs(str(video))[1]
    manifest=[{"lab":"lab1","test":test.get("name"),"slide_number":3,"source_recording":str(recording[0].relative_to(source_root)),"clip_start_seconds":round(start,3),"duration_seconds":duration,"video":str(video.resolve()),"poster":str(poster.resolve()),"video_codec":"H.264","pixel_format":"yuv420p","decode_verified":True,"editing":"Original speed; first R1 download through actual SSH installation results."}]
    (output/"manifest.json").write_text(json.dumps(manifest,ensure_ascii=False,indent=2))
    return manifest


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Make presentation clips from final Robot recordings without changing playback speed.")
    parser.add_argument("evidence_root", type=Path)
    parser.add_argument("output", type=Path)
    parser.add_argument("--lab", action="append", choices=["lab1","lab2","lab3","lab4"])
    parser.add_argument("--deployment", action="store_true", help="Lab1 D01 frontend download and SSH clip")
    options = parser.parse_args()
    print(json.dumps(make_deployment_clip(options.evidence_root, options.output) if options.deployment else make_clips(options.evidence_root, options.output, options.lab), ensure_ascii=False, indent=2))
