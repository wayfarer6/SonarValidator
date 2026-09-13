import React, { useState } from "react";
import { useParams, useLocation, useNavigate } from "react-router-dom";
import PageBreadcrumb from "../components/common/PageBreadCrumb";
import PageMeta from "../components/common/PageMeta";


export default function ViewNodes() {
  const { projectId } = useParams();
  const location = useLocation();
  const navigate = useNavigate();
  

return (
    <>
    </>
    )
}