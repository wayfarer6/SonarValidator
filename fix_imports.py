import io
import re

BASE = 'SonarValidator_Frontend/'

edits = [
    # (path, old, new)
    (BASE + 'src/App.tsx',
     'import { Navigate, Outlet } from "react-router-dom";',
     'import { Navigate } from "react-router-dom";'),

    (BASE + 'src/components/auth/SignInForm.tsx',
     'import React, { useState } from "react";',
     'import { useState } from "react";'),

    (BASE + 'src/components/auth/SignInForm.tsx',
     'const [cookies, setCookie] = useCookies(["username"]);',
     'const [, setCookie] = useCookies(["username"]);'),

    (BASE + 'src/components/auth/SignInForm.tsx',
     'const handleLogin = async (e) => {',
     'const handleLogin = async (e: React.FormEvent<HTMLFormElement>) => {'),

    (BASE + 'src/components/header/NotificationDropdown.tsx',
     'import { DropdownItem } from "../ui/dropdown/DropdownItem";\n',
     ''),

    (BASE + 'src/components/project/agent-element/AgentList.tsx',
     'import Badge from "../../ui/badge/Badge";\n',
     ''),

    (BASE + 'src/layout/AppSidebar.tsx',
     'import {\n  AlertIcon,\n  ChevronDownIcon,\n  DocsIcon,\n  FileTextIcon,\n  GlobeIcon,\n  GridIcon,\n  HorizontaLDots,\n  ListIcon,\n  MailIcon,\n  PageIcon,\n  ShieldCheckIcon,\n  UserCircleIcon,\n} from "../icons";',
     'import {\n  ChevronDownIcon,\n  DocsIcon,\n  FileTextIcon,\n  GlobeIcon,\n  GridIcon,\n  HorizontaLDots,\n  ShieldCheckIcon,\n  UserCircleIcon,\n} from "../icons";'),

    (BASE + 'src/pages/ PolicyManagement.tsx',
     'import UserMetaCard from "../components/UserProfile/UserMetaCard";\nimport UserInfoCard from "../components/UserProfile/UserInfoCard";\nimport UserAddressCard from "../components/UserProfile/UserAddressCard";\n',
     ''),

    (BASE + 'src/pages/Agent.tsx',
     'import UserMetaCard from "../components/UserProfile/UserMetaCard";\nimport UserInfoCard from "../components/UserProfile/UserInfoCard";\nimport UserAddressCard from "../components/UserProfile/UserAddressCard";\n',
     ''),

    (BASE + 'src/pages/NetworkManagement.tsx',
     'import UserMetaCard from "../components/UserProfile/UserMetaCard";\nimport UserInfoCard from "../components/UserProfile/UserInfoCard";\nimport UserAddressCard from "../components/UserProfile/UserAddressCard";\n',
     ''),

    (BASE + 'src/pages/PolicyExporter.tsx',
     'import UserMetaCard from "../components/UserProfile/UserMetaCard";\nimport UserInfoCard from "../components/UserProfile/UserInfoCard";\nimport UserAddressCard from "../components/UserProfile/UserAddressCard";\n',
     ''),

    (BASE + 'src/pages/UserProfiles.tsx',
     'import UserAddressCard from "../components/UserProfile/UserAddressCard";\n',
     ''),
]

for path, old, new in edits:
    with io.open(path, 'r', encoding='utf-8') as f:
        text = f.read()
    if old not in text:
        print('SKIP (not found):', path, '|', old.splitlines()[0][:60])
        continue
    text = text.replace(old, new, 1)
    with io.open(path, 'w', encoding='utf-8') as f:
        f.write(text)
    print('OK:', path)
