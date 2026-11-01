#!/usr/bin/env python3
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import posixpath
from xml.dom import minidom
import zipfile

P = "http://schemas.openxmlformats.org/presentationml/2006/main"
A = "http://schemas.openxmlformats.org/drawingml/2006/main"
R = "http://schemas.openxmlformats.org/officeDocument/2006/relationships"
RELS = "http://schemas.openxmlformats.org/package/2006/relationships"
P14 = "http://schemas.microsoft.com/office/powerpoint/2010/main"
MEDIA = "http://schemas.microsoft.com/office/2007/relationships/media"


def child(document, parent, namespace, name, attributes=None):
    element = document.createElementNS(namespace, name)
    for key, value in (attributes or {}).items():
        if key.startswith("r:"):
            element.setAttributeNS(R, key, value)
        else:
            element.setAttribute(key, value)
    parent.appendChild(element)
    return element


def add_recordings(source, destination, manifest_path):
    if source.resolve() == destination.resolve() or destination.exists():
        raise ValueError("Use a new destination; the source deck is preserved.")
    with zipfile.ZipFile(source) as archive:
        parts = {name: archive.read(name) for name in archive.namelist()}
    presentation = minidom.parseString(parts["ppt/presentation.xml"])
    rels = minidom.parseString(parts["ppt/_rels/presentation.xml.rels"])
    targets = {r.getAttribute("Id"): r.getAttribute("Target") for r in rels.getElementsByTagNameNS(RELS, "Relationship")}
    slide_paths = [posixpath.normpath(posixpath.join("ppt", targets[s.getAttributeNS(R, "id")])).lstrip("/")
                   for s in presentation.getElementsByTagNameNS(P, "sldId")]
    manifest = json.loads(manifest_path.read_text())
    audit = []
    for recording in manifest:
        number = recording["slide_number"]
        slide_path = slide_paths[number - 1]
        document = minidom.parseString(parts[slide_path])
        for prefix, namespace in [("p", P), ("a", A), ("r", R)]:
            document.documentElement.setAttribute("xmlns:" + prefix, namespace)
        pictures = list(document.getElementsByTagNameNS(P, "pic"))
        index = recording.get("poster_index", 0)
        if len(pictures) != 1 and "poster_index" not in recording:
            raise ValueError("Slide " + str(number) + " needs an explicit poster_index.")
        if not isinstance(index, int) or not 0 <= index < len(pictures):
            raise ValueError("Invalid poster_index on slide " + str(number))
        picture = pictures[index]
        if picture.getElementsByTagNameNS(A, "videoFile"):
            raise ValueError("Video already exists on slide " + str(number))
        nonvisual = picture.getElementsByTagNameNS(P, "cNvPr")[0]
        links = nonvisual.getElementsByTagNameNS(A, "hlinkClick")
        if links:
            link = links[0]
            if link.hasAttributeNS(R, "id"):
                link.removeAttributeNS(R, "id")
            link.setAttribute("action", "ppaction://media")
        else:
            link = child(document, nonvisual, A, "a:hlinkClick", {"action": "ppaction://media"})
            if nonvisual.firstChild is not link:
                nonvisual.insertBefore(link, nonvisual.firstChild)
        properties = picture.getElementsByTagNameNS(P, "nvPr")[0]
        video_id, media_id = "rIdSonarVideo" + str(number) + "_" + str(index), "rIdSonarMedia" + str(number) + "_" + str(index)
        video_element = child(document, properties, A, "a:videoFile", {"r:link": video_id})
        extension_lists = properties.getElementsByTagNameNS(P, "extLst")
        extensions = extension_lists[0] if extension_lists else child(document, properties, P, "p:extLst")
        properties.insertBefore(video_element, extensions)
        extension = child(document, extensions, P, "p:ext", {"uri": "{DAA4B4D4-6D71-4841-9C94-3DE7FCFB9230}"})
        media = child(document, extension, P14, "p14:media", {"r:embed": media_id})
        media.setAttribute("xmlns:p14", P14)
        video_path = Path(recording["video"])
        media_name = "ppt/media/sonar-" + str(number) + "-" + str(index) + "-" + recording["lab"] + ".mp4"
        parts[media_name] = video_path.read_bytes()
        relationship_path = str(PurePosixPath(slide_path).parent / "_rels" / (PurePosixPath(slide_path).name + ".rels"))
        relationships = minidom.parseString(parts[relationship_path])
        used = {r.getAttribute("Id") for r in relationships.getElementsByTagNameNS(RELS, "Relationship")}
        if video_id in used or media_id in used:
            raise ValueError("Relationship identifier collision")
        relative = posixpath.relpath(media_name, posixpath.dirname(slide_path))
        child(relationships, relationships.documentElement, RELS, "Relationship",
              {"Id": video_id, "Type": R + "/video", "Target": relative})
        child(relationships, relationships.documentElement, RELS, "Relationship",
              {"Id": media_id, "Type": MEDIA, "Target": relative})
        parts[slide_path] = document.toxml(encoding="UTF-8")
        parts[relationship_path] = relationships.toxml(encoding="UTF-8")
        audit.append({"slide_number": number, "poster_index": index, "lab": recording["lab"], "embedded_part": media_name,
            "bytes": len(parts[media_name]), "sha256": hashlib.sha256(parts[media_name]).hexdigest(),
            "video_relationship": video_id, "media_relationship": media_id})
    types = minidom.parseString(parts["[Content_Types].xml"])
    if not any(e.getAttribute("Extension") == "mp4" for e in types.documentElement.childNodes if e.nodeType == e.ELEMENT_NODE):
        child(types, types.documentElement, types.documentElement.namespaceURI, "Default",
              {"Extension": "mp4", "ContentType": "video/mp4"})
    parts["[Content_Types].xml"] = types.toxml(encoding="UTF-8")
    destination.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(destination, "x", zipfile.ZIP_DEFLATED) as archive:
        for name, data in parts.items():
            archive.writestr(name, data)
    for name, data in parts.items():
        if name.endswith((".xml", ".rels")):
            minidom.parseString(data)
    return {"slides": len(slide_paths), "embedded_videos": audit,
            "reference": "https://learn.microsoft.com/en-us/office/open-xml/presentation/how-to-add-a-video-to-a-slide-in-a-presentation"}


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="Embed reviewed Robot MP4 clips into the existing poster pictures of a PPTX.")
    parser.add_argument("source", type=Path)
    parser.add_argument("destination", type=Path)
    parser.add_argument("manifest", type=Path)
    parser.add_argument("--audit", type=Path, required=True)
    options = parser.parse_args()
    audit = add_recordings(options.source, options.destination, options.manifest)
    options.audit.write_text(json.dumps(audit, ensure_ascii=False, indent=2))
    print(json.dumps(audit, ensure_ascii=False, indent=2))
