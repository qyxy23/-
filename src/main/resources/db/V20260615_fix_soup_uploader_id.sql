-- 审核发布时曾误将审核员写入 uploader_id，按审核记录还原为实际上传者
UPDATE hai_gui_soup h
    INNER JOIN hai_gui_soup_audit a ON a.original_soup_id = h.soup_id
SET h.uploader_id = a.uploader_id,
    h.creator_id  = a.uploader_id
WHERE a.uploader_id IS NOT NULL
  AND h.uploader_id <> a.uploader_id;
